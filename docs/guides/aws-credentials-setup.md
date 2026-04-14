# AWS 자격증명 설정 가이드

이 문서는 환경별 AWS 자격증명 전략과 로컬 개발 설정 방법을 설명한다.

---

## 자격증명 전략 개요

`AwsConfig.awsCredentialsProvider` Bean이 환경에 따라 아래 전략을 선택한다.

| 우선순위 | 환경 | 전략 | 설정 |
|:---:|------|------|------|
| 1 | 로컬 (SSO) | `ProcessCredentialsProvider` | `AWS_PROFILE` 환경변수 지정 |
| 2 | 로컬 (비SSO) / 운영 | `DefaultCredentialsProvider` | 아래 순서로 자동 탐색 |

`DefaultCredentialsProvider` 탐색 순서:

1. 환경변수 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
2. Java 시스템 프로퍼티 `aws.accessKeyId`, `aws.secretAccessKey`
3. `~/.aws/credentials` (AWS CLI 로컬 프로파일)
4. EC2 인스턴스 프로파일 (운영 서버 IAM Role 자동 인식)

`AWS_PROFILE`이 비어있으면 항상 `DefaultCredentialsProvider`로 fallback한다.
이 전략은 이번 이슈 대응용 임시 방편이 아니라, 로컬 SSO 개발 환경의 표준 자격증명 전략이다.

---

## 로컬 설정

### SSO 사용 시 (권장)

AWS IAM Identity Center(SSO)를 사용하는 경우.

**1. SSO 로그인**
```bash
aws sso login --profile <프로파일명>
```

**2. 환경변수 설정**
```bash
export AWS_PROFILE=<프로파일명>
```

또는 `.env.local`에 추가:
```
AWS_PROFILE=<프로파일명>
```

> SSO 자격증명 로드 실패 시 → [`docs/troubleshooting/aws-sso-credentials-spring.md`](../troubleshooting/aws-sso-credentials-spring.md)

---

### 환경변수 직접 주입 시

IAM 액세스 키를 직접 사용하는 경우.

```bash
export AWS_ACCESS_KEY_ID=<키>
export AWS_SECRET_ACCESS_KEY=<시크릿>
export AWS_REGION=ap-northeast-2
```

---

### SigV4 서명 비활성화 (AWS 자격증명 없는 로컬 개발)

Lambda 혼잡도 API가 아닌 다른 기능을 개발할 때 AWS 자격증명 없이 앱을 띄우려면:

```
CONGESTION_SIGV4_ENABLED=false
```

이 플래그가 `false`이면 `CongestionClient`는 SigV4 서명 없이 요청을 보낸다.
Lambda Function URL 호출은 403으로 실패하지만, 나머지 기능은 정상 동작한다.

---

## 운영 (EC2)

EC2 인스턴스에 IAM Role이 붙어있으면 `AWS_PROFILE` 없이 `DefaultCredentialsProvider`가
인스턴스 프로파일을 자동으로 인식한다. 별도 설정 불필요.

---

## 관련 문서

- [`docs/troubleshooting/aws-sso-credentials-spring.md`](../troubleshooting/aws-sso-credentials-spring.md) — SSO 자격증명 로드 실패
- [`docs/troubleshooting/lambda-function-url-sigv4-signature-mismatch.md`](../troubleshooting/lambda-function-url-sigv4-signature-mismatch.md) — SignatureDoesNotMatch