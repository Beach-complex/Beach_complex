# Backend EC2 CD Guide

## 목적
- GitHub Actions에서 `main` 브랜치 반영 후 백엔드만 자동 배포한다.
- 배포 대상은 EC2에서 실행 중인 Docker 기반 Spring Boot 백엔드다.
- 현재 모노레포 상태이므로 `front/` 변경만 있을 때는 배포하지 않는다.

## 저장소에서 관리하는 파일
- `.github/workflows/backend-cd.yml`
- `deploy/docker-compose.ec2.yml.example`
- `deploy/backend-ec2.env.example`

## 배포 흐름
1. PR에서 기존 CI 검증
2. PR을 `main`에 merge
3. `push to main` 이벤트로 Backend CD 실행
4. Docker 이미지를 GHCR에 `latest`, `${GITHUB_SHA}` 태그로 push
5. EC2에 SSH 접속
6. EC2에서 최신 이미지 pull
7. `docker compose up -d app` 재기동
8. `http://127.0.0.1:8081/actuator/health` 헬스체크

## GitHub Secrets
- `EC2_HOST`
- `EC2_PORT`
- `EC2_USER`
- `EC2_SSH_KEY`
- `GHCR_USERNAME`
- `GHCR_READ_TOKEN`
- `EC2_DEPLOY_PATH`
- `EC2_COMPOSE_FILE`
- `EC2_ENV_FILE`

## EC2 준비 사항
1. Docker, Docker Compose 설치
2. 배포 디렉터리 생성
   - 예: `/home/ubuntu/beach-backend`
3. 운영용 compose 파일 배치
   - 예: `/home/ubuntu/beach-backend/docker-compose.yml`
4. 운영용 env 파일 배치
   - 예: `/home/ubuntu/beach-backend/.env`
   - Firebase 알림을 운영에서 사용할 경우:
     - `APP_FIREBASE_ENABLED=true`
     - `APP_FIREBASE_CREDENTIALS_JSON_BASE64=<base64-encoded-json>` 또는
     - `APP_FIREBASE_CREDENTIALS_PATH=<container-readable-path>`
5. GHCR 이미지 pull 권한이 있는 토큰 준비

## 예시 배포 디렉터리 구조
```text
/home/ubuntu/beach-backend
├── docker-compose.yml
└── .env
```

## 운영용 compose 파일 작성 기준
- 서비스명은 workflow와 맞춰 `app`으로 둔다.
- 이미지는 `${IMAGE_REPOSITORY}:${IMAGE_TAG}` 형식으로 참조한다.
- 앱 런타임 환경변수는 `EC2_ENV_FILE` 이 가리키는 서버의 env 파일에 둔다.
- 이미지 태그만 GitHub Actions가 갱신하고, 비밀값은 EC2에서 유지한다.
- Firebase 서비스 계정 키는 이미지에 포함하지 않고 EC2 env 또는 서버 파일로 주입한다.

## 첫 배포 전 확인 항목
- `main` 직접 push 제한 및 PR merge 중심 운영 여부
- GHCR push 권한 확인
- EC2에서 `docker login ghcr.io` 가능 여부
- EC2에서 `docker compose up -d app` 가능 여부
- 애플리케이션 헬스체크 URL 응답 확인

## 롤백 아이디어
- `${GITHUB_SHA}` 태그 이미지를 유지한다.
- 문제가 생기면 이전 SHA 태그를 지정해 compose를 다시 올린다.
- `latest`만 사용하는 방식은 롤백 추적이 약하므로 지양한다.

## 메모
- 현재 workflow는 백엔드 관련 파일 변경 시에만 실행되도록 `paths` 조건을 둔다.
- 현재 CI와 CD는 별도 workflow다. 필요 시 후속 작업으로 배포 전 추가 검증 단계를 더 넣을 수 있다.
