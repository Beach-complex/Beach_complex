$ErrorActionPreference = "Stop"

git config core.hooksPath .githooks

echo "Git hooks installed (core.hooksPath=.githooks)."
# ---- gitleaks auto-install (Windows, no package manager) ----
$gitleaksDir = "$env:USERPROFILE\.local\bin"
$gitleaksExe = Join-Path $gitleaksDir "gitleaks.exe"

if (-not (Test-Path $gitleaksExe)) {
  New-Item -ItemType Directory -Force -Path $gitleaksDir | Out-Null

  $version = "8.18.4"
  $zipName = "gitleaks_${version}_windows_x64.zip"
  $url = "https://github.com/gitleaks/gitleaks/releases/download/v$version/$zipName"
  $zipPath = Join-Path $env:TEMP $zipName

  Invoke-WebRequest -Uri $url -OutFile $zipPath
  Expand-Archive -Path $zipPath -DestinationPath $gitleaksDir -Force
  Remove-Item $zipPath -Force
}

# PATH에 추가 (현재 세션 + 영구)
if ($env:PATH -notlike "*$gitleaksDir*") {
  $env:PATH = "$gitleaksDir;$env:PATH"
  [Environment]::SetEnvironmentVariable(
    "PATH",
    "$gitleaksDir;$([Environment]::GetEnvironmentVariable('PATH','User'))",
    "User"
  )
}
# ------------------------------------------------------------
