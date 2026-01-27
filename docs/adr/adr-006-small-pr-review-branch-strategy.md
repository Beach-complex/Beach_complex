# ADR-006 — 작은 PR 유지를 위한 코드리뷰/브랜치 전략 (현재: CI 중심, 향후: Trunk + Feature Flag)

## 상태
Accepted

## 컨텍스트
- 우리 팀은 Java + Spring Boot 백엔드 프로젝트를 개발 중이다.
- 현재 CI(빌드/테스트 자동화)는 구축되어 있고, PR 기준으로 테스트를 통과시키며 개발을 진행 중이다.
- 하지만 배포 파이프라인(스테이징/프로덕션), 운영 메트릭/관측(모니터링/알람), 점진 롤아웃(카나리/다크 런치)은 아직 없다.
- PR이 커지면 리뷰 시간이 증가하고 병목이 생기므로, 팀 차원에서 “작은 PR(대략 300~400 라인 권장)”을 유지할 전략이 필요하다.
- 기능 하나(통합 변경 포함)가 커질 때 한 PR로 몰면 리스크/리뷰 비용이 커지고, 레이어별 분할은 “반쪽 PR”이 되어 머지 가능한 상태 유지가 어렵다.

## 결정
### 1) 현재 단계(CI 중심, 배포/관측 없음): “기본 1개 + 상황별 패턴 3개”로 운영한다
- **Primary(기본): Stacked PR(Dependent changes)**
  - 기능이 커지거나 의존성이 생겨 PR을 여러 개로 쪼개야 하는 작업은 Stacked PR로 운영한다.
- **Secondary(소형 작업): Short-lived feature branch + 빠른 머지**
  - 단순 버그 수정, 설정/문서, 단일 파일 수준 변경 등은 브랜치 1개 + PR 1개로 빠르게 끝낸다.
- **패턴 1(호환성/계약 변경): Parallel Change(Expand–Migrate–Contract)**
  - API/DTO/DB 스키마/인터페이스 등 “한 번에 바꾸면 깨질 수 있는 변경”은 단계화한다.
- **패턴 2(대규모 구조 교체): Branch by Abstraction**
  - 외부 연동 클라이언트 교체, 구현체 교체, 모듈/레이어 분리 같은 큰 구조 변경은 추상화 레이어 기반으로 공존→전환→제거로 진행한다.

### 2) 향후(배포/관측/점진 롤아웃 도입 시점): Trunk-based + Feature Flag로 단계적 전환을 추진한다
- 배포가 붙고 “main은 항상 배포 가능(releasable)”을 유지해야 하는 시점부터,
  - trunk 기반으로 작고 자주 머지하고,
  - 미완성 기능은 feature flag로 노출을 제어해 main에 계속 흘려보낸다.

## 근거
- 작은 PR은 리뷰 효율과 품질을 높이고(변경 목적이 명확, 리뷰 부담 감소), 충돌/리스크를 낮추는 업계 공통 관행이다.
- 현재는 배포/관측/점진 롤아웃이 없으므로, “운영(릴리즈/노출 제어)”보다 “리뷰/통합 효율”이 우선이다.
- Stacked PR은 배포/메트릭이 없어도 즉시 효과가 나며(리뷰 단위 축소, 의존 변경을 순서 있게 통합), 대규모 코드베이스에서도 정식 프로세스로 운영되는 사례가 많다.
- Trunk + Feature Flag는 배포-릴리즈 분리와 점진 노출에서 ROI가 크다. 따라서 배포/관측/롤아웃 체계가 갖춰지는 시점에 전환하는 편이 비용 대비 효과가 좋다.

## 결과
- 팀의 기본 선택지는 **Stacked PR**이다. 단, 모든 작업을 스택으로 강제하지 않는다.
- 변경 성격에 따라 우선 적용 기준은 다음과 같다.
  - 작은 단일 변경 → short-lived feature branch
  - 호환성/계약/스키마 변경 → Parallel Change
  - 대규모 구조 교체 → Branch by Abstraction
- 운영 디테일(체크리스트/예시/구체 규율/지표 등)은 ADR이 아니라 **운영 문서(플레이북)** 로 관리한다.
  - 예: `docs/engineering/small-pr-policy.md` (별도 문서)

## 대안
1) 지금 당장 Trunk + Feature Flag로 전환  
- 보류: 배포/관측/점진 롤아웃이 없는 단계에서는 핵심 효용(배포-릴리즈 분리)을 활용하기 어렵고, 토글 수명 관리/분기 테스트 비용이 먼저 커질 수 있다.

2) 큰 PR 허용(기능 단위 한 방)  
- 미채택: 리뷰 비용/결함 위험이 상승하고, 업계 가이드들이 작은 PR을 권장한다.

3) 레이어별 분할 PR(controller/service/repo 따로)  
- 미채택: 중간 PR이 반쪽이 되어 리뷰 가치가 떨어지고 머지 전략이 꼬이기 쉽다. (대신 “완결된 작은 단위 + 의존성 체인”으로 해결한다.)

## 참고
- 공식 문서 링크
  - GitHub — Helping others review your changes: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/helping-others-review-your-changes
  - Microsoft Engineering Playbook — Pull Requests: https://microsoft.github.io/code-with-engineering-playbook/code-reviews/pull-requests/
  - Google Engineering Practices — Small CLs: https://google.github.io/eng-practices/review/developer/small-cls.html
  - Gerrit — Concept changes: https://gerrit-review.googlesource.com/Documentation/concept-changes.html
  - Chromium — Contributing docs: https://source.chromium.org/chromium/chromium/src/+/main:docs/contributing.md
  - Fuchsia — Dependent changes: https://fuchsia.dev/fuchsia-src/development/source_code/dependent_changes
  - Martin Fowler — Feature Toggles: https://martinfowler.com/articles/feature-toggles.html
  - DORA — Trunk-based development: https://dora.dev/capabilities/trunk-based-development/
  - DORA — Working in small batches: https://dora.dev/capabilities/working-in-small-batches/
  - Martin Fowler — Parallel Change: https://martinfowler.com/bliki/ParallelChange.html
  - Martin Fowler — Branch by Abstraction: https://martinfowler.com/bliki/BranchByAbstraction.html
  - AWS — Branch by abstraction pattern: https://docs.aws.amazon.com/prescriptive-guidance/latest/modernization-decomposing-monoliths/branch-by-abstraction.html
- 관련 이슈 / PR
  - (팀에서 링크 추가)

## 작성일자
2026-01-27
