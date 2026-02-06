# 라인엔딩 변경으로 파일이 수정된 것처럼 보이는 문제

**작성일:** 2026-01-26

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-01-20 | - | 문서 생성 |

## 문제 상황

### 증상
- `git status`에 Java 파일이 수정된 것으로 표시되는데,
  `git diff`에서는 내용 변경이 보이지 않음.

### 재현 확인
```
git status -s -- src/main/java/com/beachcheck/config/SecurityConfig.java src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java
git diff -- src/main/java/com/beachcheck/config/SecurityConfig.java src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java
```

## 조사 과정

### 1) 내용 변경 여부 확인
```
git diff -- src/main/java/com/beachcheck/config/SecurityConfig.java src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java
```
- 결과: 내용 차이 없음

### 2) 라인엔딩 상태 확인
```
git ls-files --eol src/main/java/com/beachcheck/config/SecurityConfig.java src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java
```
- 결과:
  - `i/lf w/crlf`
  - 인덱스는 LF, 워킹트리는 CRLF로 변환된 상태

### 3) Git 설정 확인
```
git config --show-origin core.autocrlf
```
- 결과: `true`

## 원인
- `core.autocrlf=true` 설정으로 인해
  Git이 워킹 트리에서 라인엔딩을 CRLF로 변환했다.
- 따라서 내용 변경이 없어도 “수정됨”으로 보인다.

## 해결 과정

### 1) 원상 복구
```
git restore -- src/main/java/com/beachcheck/config/SecurityConfig.java
git restore -- src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java
```

### 2) 결과 확인
```
git status -s -- src/main/java/com/beachcheck/config/SecurityConfig.java src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java
```
- 수정 표시 해제됨

## 참고
- 장기적으로는 `.gitattributes`에 `*.java text eol=lf` 같은 정책을 두는 방법도 있음.
- 다만 이 작업은 프로젝트 전역 정책 변경이므로 별도 이슈/브랜치에서 합의 후 진행하는 편이 안전하다.
