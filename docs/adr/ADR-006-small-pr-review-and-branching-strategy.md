# ADR-006 — 작은 PR 유지를 위한 코드리뷰/브랜치 전략 (현 단계: Stacked PR, 향후: Trunk + Feature Flag)

## 상태
Accepted

## 컨텍스트
- 우리 팀은 백엔드(Java + Spring Boot) 프로젝트를 개발 중이다.
- 현재 **CI(빌드/테스트 자동화)는 구축**되어 있고, PR 기준으로 테스트를 통과시키며 개발을 진행 중이다.
- 하지만 **배포 파이프라인(스테이징/프로덕션)과 운영 메트릭/관측(모니터링, 알람), 점진 롤아웃(카나리/다크 런치) 체계는 아직 없다.**
- PR이 커지면 리뷰 시간이 폭증하고 병목이 생겨, 팀 내에서 **PR 크기 목표를 300~400 라인 수준**으로 두고 운영하려 한다.
- 문제는 “기능 하나(통합 변경 포함)”가 커질 때:
  - 한 PR로 몰면 리뷰/리스크가 커지고,
  - 레이어별(Controller/Service/Repo)로 쪼개면 중간 PR이 반쪽이 되어 “머지 가능한 상태”를 유지하기 어렵다.

## 결정
### 1) 현재 단계(배포/메트릭 없음): **Stacked PR(= Dependent Changes) 중심으로 운영한다**
큰 기능은 “연속된 여러 PR”로 쪼개고, PR 간 의존 관계를 명시한다.

**운영 규칙**
- 각 PR은 다음을 만족해야 한다.
  - **단일 목적**(PR 제목/본문 한 문장으로 “무엇을 위해 바꿨는지” 설명 가능)
  - **CI Green**(빌드/테스트 통과)
  - 가능한 한 **독립적으로 이해 가능한 변경**(다음 PR을 안 봐도 “왜 필요한지” 파악 가능)
- PR 본문 상단에 의존 관계를 명시한다.
  - 예: `Depends on: #123` / 없으면 `Depends on: None`
- 머지 순서는 **기반 PR → 후속 PR** 순서로 한다.

### 2) 향후(배포/관측/점진 롤아웃이 붙는 시점): **Trunk-based + Feature Flag를 단계적으로 도입한다**
배포가 붙고 “main은 항상 배포 가능”을 유지해야 하는 시점부터,
- trunk 기반으로 **작고 자주 머지**하고,
- 미완성 기능은 **feature flag로 노출을 제어**해 main에 계속 흘려보낸다.

## 근거
### 공통 전제: “작은 PR”은 업계 공통 베스트 프랙티스
- GitHub는 리뷰를 돕기 위해 **작고, 단일 목적에 집중된 PR**을 권장한다.  
  - https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/helping-others-review-your-changes
- Microsoft Engineering Playbook은 **PR을 항상 작게 유지**하라고 하며, 작을수록 리뷰/충돌/릴리즈 측면에서 유리하다고 정리한다.  
  - https://microsoft.github.io/code-with-engineering-playbook/code-reviews/pull-requests/
- Google Engineering Practices는 큰 변경도 보통 **작은 변경들의 연속으로 쪼갤 수 있다**는 관점을 명시한다.  
  - https://google.github.io/eng-practices/review/developer/small-cls.html

### 왜 “지금은 Stacked PR”이 1순위인가
- Stacked PR은 **배포/메트릭이 없어도 즉시 효과**가 난다(리뷰 단위 축소 + 병렬 작업 가능).
- 대규모 코드베이스(Gerrit 문화권)에서 dependent changes는 “공식 기능/프로세스”로 존재한다.
  - Gerrit 개념 문서(변경/체인/토픽 등 change 모델):  
    https://gerrit-review.googlesource.com/Documentation/concept-changes.html
  - Chromium contributing(land를 기다리지 않고 진행할 때 dependent changes 사용 안내 포함):  
    https://source.chromium.org/chromium/chromium/src/+/main:docs/contributing.md
  - Fuchsia dependent changes (`Depends-on` footer로 의존 체인 운영):  
    https://fuchsia.dev/fuchsia-src/development/source_code/dependent_changes

**현 시점에서의 비용 관점**
- Trunk + Feature Flag는 “배포/릴리즈 분리, 점진 노출”에서 진가가 큰데, 현재는 배포/관측이 없어서 그 이점을 당장 활용하기 어렵다.
- 반면 Stacked PR은 “리뷰/CI 단계”만으로도 ROI가 나온다.

### 왜 “나중에는 Trunk + Feature Flag”가 유리해지는가
- DORA는 trunk-based development를 “작고 빈번한 머지(small & frequent merges)”라는 방향으로 설명한다.  
  - https://dora.dev/capabilities/trunk-based-development/
- DORA는 작은 배치로 작업할 때 feature toggle로 **dark launch(노출 제어)**하는 접근을 구체적으로 다룬다.  
  - https://dora.dev/capabilities/working-in-small-batches/
- AWS Prescriptive Guidance는 Git 기반 개발 베스트 프랙티스로 **“작고 자주” + “feature toggles 사용”**을 명시한다.  
  - https://docs.aws.amazon.com/prescriptive-guidance/latest/choosing-git-branch-approach/best-practices-for-git-based-development.html
- Atlassian은 trunk-based에서 feature flags가 **별도 feature branch 없이 main에 커밋 가능하게 해준다**고 설명한다.  
  - https://www.atlassian.com/continuous-delivery/continuous-integration/trunk-based-development
- Martin Fowler는 feature toggles(특히 release toggle)를 **배포와 릴리즈 분리 원칙을 구현**하는 대표 기법으로 정리한다.  
  - https://martinfowler.com/articles/feature-toggles.html
- Unleash는 trunk-based에서 **미완성 코드를 main에 머지하고 flag로 숨겨 trunk를 releasable로 유지**하는 운영을 직접적으로 설명한다.  
  - https://docs.getunleash.io/guides/trunk-based-development

### Java + Spring에서 토글 구현이 현실적인 이유
- Spring Boot는 `@ConditionalOnProperty`를 공식 API로 제공한다(프로퍼티 기반 on/off).  
  - https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html

## 결과
### (A) 즉시 적용: Stacked PR 운영 지침(팀 룰)
**PR 시퀀스 예시(권장 3~4개로 끝내기)**
1. PR1(기반): 계약/뼈대
   - DTO/validation/라우팅
   - 통합테스트 “성공 1개” 또는 최소 계약 테스트 1개
2. PR2: 핵심 로직
   - service/repo 구현 + 핵심 시나리오 테스트 확장
3. PR3: 엣지/에러/동시성
   - 에러코드/예외/동시성/정합성 + 테스트 보강
4. PR4(선택): 정리/문서
   - 리팩터/문서화/불필요 코드 제거

**PR 템플릿(권장)**
- 요약(1~2줄): 무엇을 해결하는 PR인지
- Depends on: #번호 / None
- 변경 범위:
- 테스트 방법(로컬/CI 기준):

**머지 규칙**
- 항상 CI Green에서만 머지
- 기반 PR 머지 후 후속 PR은 rebase(또는 base 갱신)해서 충돌 최소화

### (B) 배포/관측 도입 시점에 추가할 것(Trunk+Flag 전환 체크)
- 브랜치 수명 목표를 “짧게(1~2일 내 머지)”로 두고, 큰 기능은 토글로 나눠서 main에 흘린다.
- 토글 수명 관리(필수)
  - 토글마다 Owner/목적/삭제 예정일 기록
  - 롤아웃 완료 후 **토글 제거 PR**을 완료 조건에 포함
- 토글 경로 ON/OFF 테스트 전략 추가(적어도 핵심 경로는 둘 다 깨지지 않게)

## 대안
1) **지금 당장 Trunk + Feature Flag로 전환**
- 장점: 장기적으로 “main에 계속 통합 + 노출 제어”가 강력함
- 미채택 이유(현 시점): 배포/관측/점진 롤아웃이 없어 핵심 효용을 당장 못 뽑고, 토글/분기 테스트/수명 관리 비용이 먼저 커질 수 있음

2) **큰 PR을 허용(기능 단위로 한 방에 올리기)**
- 미채택 이유: GitHub/Microsoft/Google 가이드 모두 “작고 집중된 PR”을 권장하며, 큰 PR은 리뷰 비용/결함 위험이 상승

3) **레이어별 분할 PR(Controller/Service/Repo 따로)**
- 미채택 이유: 중간 PR이 “완성되지 않은 반쪽”이 되어 리뷰 가치가 떨어지고 머지 전략이 꼬이기 쉬움  
  (대신 본 ADR은 “완결된 작은 단위 + 체인(의존성) 명시”로 해결)

## 참고
### 공식 문서 링크
- 작은 PR 원칙
  - GitHub: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/helping-others-review-your-changes
  - Microsoft Playbook: https://microsoft.github.io/code-with-engineering-playbook/code-reviews/pull-requests/
  - Google Small CLs: https://google.github.io/eng-practices/review/developer/small-cls.html

- Trunk-based + Feature Flag
  - DORA trunk-based: https://dora.dev/capabilities/trunk-based-development/
  - DORA small batches: https://dora.dev/capabilities/working-in-small-batches/
  - AWS best practices: https://docs.aws.amazon.com/prescriptive-guidance/latest/choosing-git-branch-approach/best-practices-for-git-based-development.html
  - Atlassian trunk-based: https://www.atlassian.com/continuous-delivery/continuous-integration/trunk-based-development
  - Martin Fowler feature toggles: https://martinfowler.com/articles/feature-toggles.html
  - Unleash trunk-based + flags: https://docs.getunleash.io/guides/trunk-based-development
  - Spring Boot ConditionalOnProperty: https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html

- Stacked PR (Dependent Changes)
  - Gerrit concepts: https://gerrit-review.googlesource.com/Documentation/concept-changes.html
  - Chromium contributing: https://source.chromium.org/chromium/chromium/src/+/main:docs/contributing.md
  - Fuchsia dependent changes: https://fuchsia.dev/fuchsia-src/development/source_code/dependent_changes

### 관련 이슈 / PR
- (팀에서 링크 추가)

## 작성일자
2026-01-27
