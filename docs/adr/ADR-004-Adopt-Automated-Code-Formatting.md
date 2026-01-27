# ADR-004: 코드 포맷 자동화 도입 (Spotless + google-java-format)

## 상태
Accepted

## 컨텍스트
- 현재 코드베이스는 공백/들여쓰기/줄바꿈 스타일이 혼재되어 있으며, 리뷰 시 스타일 이슈가 반복적으로 발생한다.
- 최소 정적 테스트(Checkstyle)가 도입되면서 스타일 정합성을 자동화할 필요가 커졌다.
- IDE별 포맷 설정 차이로 인해 동일 코드에 서로 다른 포맷이 적용되는 문제가 있다.
- 포맷을 자동화해 리뷰를 로직 중심으로 집중시키고자 한다.

## 결정
- Java 코드 포맷 자동화를 위해 **Spotless + google-java-format**을 도입한다.
- CI에서 `spotlessCheck`를 통해 포맷 불일치 시 실패하도록 한다.
- 최초 적용은 **포맷 전용 PR**로 분리해 대량 변경을 안전하게 처리한다.

## 근거
- google-java-format은 강한 규칙 기반으로 팀 간 스타일 논쟁을 제거한다.
- Spotless는 Gradle 통합이 간단하고, `spotlessApply/spotlessCheck`로 운영이 명확하다.
- 도입/운영 비용이 낮아 유지보수가 용이하다.

## 결과
- **긍정적 영향**
  - 코드 스타일 일관성 확보
  - 리뷰에서 스타일 논쟁 감소, 로직 중심 리뷰 가능
- **주의 사항**
  - 최초 적용 시 대량 포맷 변경 발생
  - google-java-format은 포맷 규칙 커스터마이징이 거의 불가능
  - IDE 포맷 설정과 불일치할 수 있어 플러그인 도입 또는 사용 가이드 필요

## 대안
- Spotless + Eclipse formatter: 커스터마이징 가능하나 설정 관리 비용 증가
- Checkstyle만 유지: 자동 포맷 없음, 스타일 불일치 지속
- IDE 포맷 통일 + CI 검사: 설정 공유/강제가 어려움

## 참고
- google-java-format: https://github.com/google/google-java-format
- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- (관련 이슈/PR 링크)

## 작성일자
2026-01-12
