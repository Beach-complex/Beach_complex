# ADR-007 — 배포 전(CI 중심) 단계에서 “작은 PR”을 유지하기 위한 운영 정책 (4가지 옵션 선택 기준 포함)

## 상태
Accepted

## 컨텍스트
- 우리 팀은 Java + Spring Boot 백엔드 프로젝트를 개발 중이며, 현재는 **CI(빌드/테스트)** 까지 구축된 상태다.
- **배포 파이프라인(스테이징/프로덕션)과 운영 메트릭/관측(모니터링/알람), 점진 롤아웃(카나리/다크런치)** 은 아직 없다.
- PR이 커지면 리뷰가 느려지고 품질이 떨어지기 쉬워, 팀 차원에서 “작은 PR”을 유지하는 정책이 필요하다.
- 후보 방법은 다음 4가지다.
  1) Stacked PR (Dependent changes)
  2) Short-lived feature branch + 빠른 머지 규율 (GitHub Flow/PR 중심)
  3) Parallel Change (Expand–Migrate–Contract)
  4) Branch by Abstraction

## 결정
### 결론: “하나만 고르는” 대신, **기본 1개 + 상황별 패턴 3개**로 운영한다.
- **기본(Primary): Stacked PR**
  - “기능 하나가 커져서 PR이 300~400 라인을 넘기기 쉬운 작업”은 Stacked PR로 쪼갠다.
- **소형 작업(Secondary): Short-lived feature branch**
  - 작은 버그 수정/단일 파일 수준 변경/단순 설정 변경은 짧은 브랜치 1개 + PR 1개로 끝낸다.
- **호환성/계약 변경 패턴: Parallel Change (Expand–Migrate–Contract)**
  - API/DTO/DB 스키마/인터페이스 변경 등 “한 번에 바꾸면 깨질 수 있는 변경”은 이 패턴을 적용한다.
- **대규모 구조 교체 패턴: Branch by Abstraction**
  - 외부 연동 클라이언트 교체, 모듈/레이어 분리, 저장소/구현체 교체 등 큰 구조 변경은 추상화 레이어를 도입해 단계적으로 전환한다.

> 즉, “작은 PR 유지”라는 목표는 동일하지만, 변경 성격에 따라 **가장 비용 대비 효과가 좋은 도구를 선택**한다.

## 근거
### 1) 작은 PR은 업계 공통 가이드(공식/권위 자료가 많음)
- 작은 PR은 리뷰가 쉽고 빠르며, 변경 목적이 명확해진다는 권고가 반복적으로 등장한다.
  - (참고 링크: GitHub Docs / Microsoft Engineering Playbook / Google Engineering Practices)

### 2) 배포/메트릭이 없는 단계에서는 “운영(릴리즈/노출) 제어”보다 “리뷰/통합 효율”이 우선이다
- Feature Flag/Trunk 기반은 “배포-릴리즈 분리”에서 ROI가 커지는데, 지금은 배포/관측이 없으므로 우선순위를 뒤로 미룬다(향후 ADR로 재검토).

### 3) Stacked PR은 “기능이 커져도 PR을 작게 유지”하는 데 가장 직접적이다
- 큰 변화(기능)를 여러 개의 작은 리뷰 단위로 나누되, 서로 의존 관계를 명시하고 순서대로 머지한다.
- Gerrit/Chromium/Fuchsia 같은 대규모 조직·코드베이스에서 dependent changes는 “공식 운영 방식”으로 다뤄진다.

### 4) Parallel Change / Branch by Abstraction은 “특정 유형에서 강력한 표준 패턴”이다
- 깨질 수 있는 변경(스키마/계약 변경)은 Expand–Migrate–Contract로 안전하게 단계화한다.
- 대규모 교체(리팩터/모듈 교체)는 추상화 레이어로 공존→전환→제거 흐름을 만든다.
- AWS Prescriptive Guidance도 Branch by Abstraction을 현대화/분해 패턴으로 문서화한다.

## 결과
### A) 운영 규칙(팀 정책)
#### A-1. 선택 기준(결정 트리)
아래 기준 중 하나라도 해당하면 **Stacked PR**을 기본으로 한다.
- “하나의 PR로 끝내기 어렵다(2~3일 이상 걸린다)”
- “변경이 여러 레이어(controller/service/repo/infra/test)를 가로지른다”
- “리뷰어가 한 번에 이해하기 어려운 규모(파일 수/변경 범위)가 된다”
- “다음 작업을 진행하려면 기반 변경이 먼저 필요하다(의존성 존재)”

반대로 아래 조건을 만족하면 **Short-lived feature branch**로 간다.
- “변경 목적이 단일하고, PR 1개(작은 범위)로 끝낼 수 있다”
- “의존 PR이 필요 없다”
- “테스트/검증이 단순하다”

아래 유형은 **패턴을 우선 적용**한다.
- (호환성/계약/스키마 변경) → **Parallel Change**
- (대규모 구조 교체/모듈화/구현체 교체) → **Branch by Abstraction**

#### A-2. PR 템플릿(필수 항목)
PR 본문 상단에 다음을 작성한다.
- **Purpose(1~2줄):** 이 PR이 해결하는 문제/목적
- **Scope:** 변경 범위(무엇이 포함/제외인지)
- **Test:** 로컬/CI에서 어떻게 검증했는지
- **Depends on:** `#PR번호` 또는 `None` (Stacked PR이면 반드시 작성)

#### A-3. Stacked PR 작성 규칙(실무형)
- 스택은 보통 **3~4개 PR**을 권장한다. (5개 이상이면 분해 전략을 다시 점검)
- 각 PR은 “머지 가능한 상태”여야 한다.
  - CI Green
  - 단일 목적
  - 가능한 한 독립적으로 이해 가능
- 리뷰는 **기반 PR → 후속 PR** 순서로 진행한다.
- 기반 PR 머지 후, 후속 PR은 base 갱신(rebase/merge)으로 충돌을 최소화한다.

> GitHub에서 Stacked를 쓸 때는 “각 PR의 base branch를 이전 PR 브랜치로 지정”하거나,
> PR 본문에 Depends-on을 명시해 리뷰어가 관계를 따라갈 수 있게 한다.
> (도구는 필수 아님. 필요 시 Graphite/Sapling 등도 검토 가능)

#### A-4. Parallel Change(Expand–Migrate–Contract) 적용 체크리스트
- Expand: 새 스키마/필드/인터페이스를 추가하되, 기존과 **동시 호환**되게 만든다.
- Migrate: 호출부/데이터를 새 방식으로 이동한다(읽기/쓰기 전환 포함).
- Contract: 기존 방식 제거(구필드/구경로 삭제) + 정리 PR

#### A-5. Branch by Abstraction 적용 체크리스트
- 추상화 레이어(인터페이스/포트)를 먼저 만든다.
- 기존 구현(legacy)을 추상화 뒤로 숨긴다.
- 새 구현(new)을 같은 추상화로 추가한다.
- 호출부를 단계적으로 new로 전환한다.
- legacy 제거 + 정리 PR

### B) 도입 절차(바로 실행 가능한 수준)
1) PR 템플릿에 `Depends on` 항목 추가
2) “작은 PR” 기준을 팀 합의로 문서화(예: 300~400 라인 권장)
3) 스택 운영 룰 합의(리뷰 순서, 최대 스택 길이, base 갱신 방식)
4) 호환성 변경 시 Parallel Change 강제(체크리스트)
5) 큰 구조 변경은 Branch by Abstraction으로 계획 먼저 세우기(인터페이스/전환 계획)

### C) 성공 측정(배포/메트릭 없어도 가능한 것)
- GitHub에서 다음을 주간 단위로 확인한다.
  - PR 평균 변경량(라인/파일 수)
  - 리뷰 첫 응답 시간
  - 머지까지 걸린 시간
  - “리뷰 스킵/대충 승인” 발생 여부(정성 피드백)

## 대안
1) “큰 PR 허용(기능 단위 한 방에)”  
- 미채택: 공식 가이드들이 공통으로 “작고 단일 목적 PR”을 권장하며, 큰 PR은 리뷰 부담이 커진다.

2) “지금 당장 Trunk-based + Feature Flag로 전환”  
- 보류: 배포/관측/점진 롤아웃이 없는 단계에서는 운영 가치가 제한적이다. (향후 배포 도입 시 ADR로 재검토)

3) “레이어별 분할 PR(Controller/Service/Repo 따로)”  
- 미채택: 중간 PR이 반쪽이 되어 리뷰 가치가 떨어지고, 결국 다시 PR이 커지기 쉽다.  
  (대신 ‘완결된 작은 단위 + 의존성 명시(스택)’로 해결)

## 참고
### 작은 PR(공식/권위)
- GitHub Docs — Helping others review your changes (small, focused PR 권장)  
  https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/helping-others-review-your-changes
- Microsoft Engineering Playbook — Pull Requests (작은 PR 권장)  
  https://microsoft.github.io/code-with-engineering-playbook/code-reviews/pull-requests/
- Google Engineering Practices — Small CLs (100 라인 권장, 1000 라인은 대체로 큼)  
  https://google.github.io/eng-practices/review/developer/small-cls.html

### Short-lived feature branch(실무 지침)
- AWS Well-Architected DevOps Guidance — Keep feature branches short-lived (하루 1회 이상 main에 머지 권장)  
  https://docs.aws.amazon.com/wellarchitected/latest/devops-guidance/dl.scm.2-keep-feature-branches-short-lived.html
- GitHub Docs — GitHub flow  
  https://docs.github.com/get-started/quickstart/github-flow

### Stacked PR / Dependent changes(대규모 실무 사례)
- Chromium contributing — Uploading dependent changes (Gerrit dependent changes)  
  https://github.com/chromium/chromium/blob/main/docs/contributing.md
- Fuchsia — dependent changes (Depends-on 운영)  
  https://fuchsia.dev/fuchsia-src/development/source_code/dependent_changes
- Gerrit Concepts — Topics / Relation Chain (관련 변경 묶기)  
  https://gerrit-review.googlesource.com/Documentation/concept-changes.html
- Gerrit 3.8 — Rebase a chain of changes (체인 리베이스 지원)  
  https://www.gerritcodereview.com/3.8.html
- Meta Engineering Blog — Sapling & ReviewStack(스택 리뷰 지원을 위한 도구/UX 언급)  
  https://engineering.fb.com/2022/11/15/open-source/sapling-source-control-scalable/
- Sapling Docs — Stacks of commits  
  https://sapling-scm.com/docs/overview/stacks/

### Parallel Change(Expand–Migrate–Contract)
- Martin Fowler — Parallel Change (expand/migrate/contract)  
  https://martinfowler.com/bliki/ParallelChange.html
- Martin Fowler — Continuous Integration (parallel change 예시 포함)  
  https://martinfowler.com/articles/continuousIntegration.html

### Branch by Abstraction
- Martin Fowler — Branch By Abstraction  
  https://martinfowler.com/bliki/BranchByAbstraction.html
- AWS Prescriptive Guidance — Branch by abstraction pattern  
  https://docs.aws.amazon.com/prescriptive-guidance/latest/modernization-decomposing-monoliths/branch-by-abstraction.html

### (향후 검토) Trunk-based + Feature Flag 근거
- DORA — Trunk-based development (small & frequent merges)  
  https://dora.dev/capabilities/trunk-based-development/
- DORA — Working in small batches (feature toggle로 dark launch)  
  https://dora.dev/capabilities/working-in-small-batches/
- Atlassian — Trunk-based development (feature flags가 trunk-based를 보완)  
  https://www.atlassian.com/continuous-delivery/continuous-integration/trunk-based-development

## 작성일자
2026-01-27
