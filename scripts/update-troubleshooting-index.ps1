$ErrorActionPreference = "Stop"
$strictCreatedDate = $env:TS_INDEX_STRICT_CREATED_DATE -eq "1"

function Normalize-Date {
    param([string]$Raw)
    if (-not $Raw) {
        return $null
    }

    $normalized = $Raw -replace "\.", "-" -replace "/", "-"
    $parts = $normalized.Split("-")
    if ($parts.Count -lt 3) {
        return $null
    }

    $year = $parts[0]
    $month = $parts[1].PadLeft(2, "0")
    $day = $parts[2].PadLeft(2, "0")
    $date = "$year-$month-$day"

    if ($date -match "^\d{4}-\d{2}-\d{2}$") {
        return $date
    }

    return $null
}

function Extract-Title {
    param([string]$Content)
    if ($Content -match "(?m)^#\s*(.+)$") {
        return $Matches[1].Trim()
    }
    return $null
}

function Extract-DateFromLine {
    param([string]$Line)
    if (-not $Line) {
        return $null
    }
    if ($Line -match "\d{4}[./-]\d{1,2}[./-]\d{1,2}") {
        return (Normalize-Date $Matches[0])
    }
    return $null
}

function Extract-Date {
    param([string]$Content)
    $lines = $Content -split "`r?`n"

    $resolvedLine = ($lines | Where-Object { $_ -match "해결\s*날짜" } | Select-Object -First 1)
    $resolvedDate = Extract-DateFromLine $resolvedLine
    if ($resolvedDate) {
        return $resolvedDate
    }

    $createdLine = ($lines | Where-Object { $_ -match "작성일" } | Select-Object -First 1)
    $createdDate = Extract-DateFromLine $createdLine
    if ($createdDate) {
        return $createdDate
    }

    return $null
}

function Extract-CreatedDate {
    param([string]$Content)
    $lines = $Content -split "`r?`n"
    $createdLine = ($lines | Where-Object { $_ -match "작성일" } | Select-Object -First 1)
    return (Extract-DateFromLine $createdLine)
}

function Extract-Components {
    param([string]$Content)
    $line = ($Content -split "`r?`n" | Where-Object { $_ -match "컴포넌트" } | Select-Object -First 1)
    if (-not $line) {
        return @()
    }

    if ($line -match "컴포넌트\*?\*?\s*[:：]\*?\*?\s*(.+)$") {
        $raw = $Matches[1]
    } else {
        return @()
    }

    return ($raw -split "[,/]" | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ })
}

function Component-Bucket {
    param([string]$Component)
    switch -regex ($Component) {
        "api|web" { return "API_WEB" }
        "auth" { return "AUTH" }
        "db|database|migration" { return "DB_MIGRATION" }
        "infra|devx|docker|ci|tool|tooling|hook" { return "INFRA_DEVX" }
        default { return $null }
    }
}

function Replace-Section {
    param(
        [string]$Content,
        [string]$StartToken,
        [string]$EndToken,
        [string]$NewLines
    )
    $startIndex = $Content.IndexOf($StartToken)
    if ($startIndex -lt 0) {
        throw "Start token not found: $StartToken"
    }

    $endIndex = $Content.IndexOf($EndToken, $startIndex)
    if ($endIndex -lt 0) {
        throw "End token not found: $EndToken"
    }

    $before = $Content.Substring(0, $startIndex + $StartToken.Length)
    $after = $Content.Substring($endIndex)
    return "$before`r`n$NewLines`r`n$after"
}

function Escape-LinkText {
    param([string]$Text)
    if (-not $Text) {
        return $Text
    }
    return ($Text -replace '\[', '\[' -replace '\]', '\]')
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$docsDir = Join-Path $repoRoot "docs\troubleshooting"
$indexPath = Join-Path $docsDir "docs-README.md"

$files = Get-ChildItem $docsDir -Filter "*.md" |
    Where-Object {
        $_.Name -notin @("docs-README.md", "template.md", "troubleshooting-template.md")
    }

$entries = foreach ($file in $files) {
    $content = Get-Content -Raw -Encoding utf8 $file.FullName
    $title = Extract-Title $content
    $date = Extract-Date $content
    $createdDate = Extract-CreatedDate $content

    $sortDate = if ($date) {
        [DateTime]::ParseExact($date, "yyyy-MM-dd", $null)
    } else {
        [DateTime]::MinValue
    }

    $components = Extract-Components $content
    $buckets = $components |
        ForEach-Object { Component-Bucket $_ } |
        Where-Object { $_ } |
        Select-Object -Unique

    [pscustomobject]@{
        File = $file.Name
        Title = $title
        Date = $date
        CreatedDate = $createdDate
        SortDate = $sortDate
        Buckets = $buckets
    }
}

$missingCreatedDate = $entries | Where-Object { -not $_.CreatedDate } | Sort-Object -Property File
if ($missingCreatedDate.Count -gt 0) {
    $missingList = ($missingCreatedDate | ForEach-Object { $_.File }) -join ", "
    Write-Warning "작성일 메타가 누락된 문서: $missingList"
    Write-Warning "권장: 각 문서 상단에 '**작성일:** YYYY-MM-DD'를 추가하세요."
    if ($strictCreatedDate) {
        throw "작성일 메타 누락 문서가 있습니다. (TS_INDEX_STRICT_CREATED_DATE=1)"
    }
}

$recentLines = $entries |
    Sort-Object -Property @{ Expression = "SortDate"; Descending = $true }, @{ Expression = "Title"; Descending = $false }, @{ Expression = "File"; Descending = $false } |
    ForEach-Object {
        $titleValue = if ($_.Title) { $_.Title } else { [System.IO.Path]::GetFileNameWithoutExtension($_.File) }
        $title = Escape-LinkText $titleValue
        if ($_.Date) {
            "- [$($_.Date)] [$title](./$($_.File))"
        } else {
            "- [$title](./$($_.File))"
        }
    }

$content = Get-Content -Raw -Encoding utf8 $indexPath
$content = Replace-Section $content "<!-- INDEX:RECENT:START -->" "<!-- INDEX:RECENT:END -->" ($recentLines -join "`r`n")

$hasComponentData = $entries | Where-Object { $_.Buckets.Count -gt 0 }
if ($hasComponentData) {
    $componentMap = @{
        "API_WEB" = "<!-- INDEX:API_WEB:START -->"
        "AUTH" = "<!-- INDEX:AUTH:START -->"
        "DB_MIGRATION" = "<!-- INDEX:DB_MIGRATION:START -->"
        "INFRA_DEVX" = "<!-- INDEX:INFRA_DEVX:START -->"
    }

    foreach ($bucket in $componentMap.Keys) {
        $lines = $entries |
            Where-Object { $_.Buckets -contains $bucket } |
            Sort-Object -Property @{ Expression = "SortDate"; Descending = $true }, @{ Expression = "Title"; Descending = $false }, @{ Expression = "File"; Descending = $false } |
            ForEach-Object {
                $titleValue = if ($_.Title) { $_.Title } else { [System.IO.Path]::GetFileNameWithoutExtension($_.File) }
                $title = Escape-LinkText $titleValue
                if ($_.Date) {
                    "- [$($_.Date)] [$title](./$($_.File))"
                } else {
                    "- [$title](./$($_.File))"
                }
            }

        if (-not $lines) {
            $lines = @("- (없음)")
        }

        $startToken = $componentMap[$bucket]
        $endToken = $startToken -replace "START", "END"
        $content = Replace-Section $content $startToken $endToken ($lines -join "`r`n")
    }
}

Set-Content -Encoding utf8 $indexPath $content
