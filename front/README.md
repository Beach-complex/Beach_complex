# 비치체크 프론트엔드

부산 해수욕장 혼잡도 및 알림 서비스 - React 프론트엔드

## 🚀 빠른 시작 (Quick Start)

```bash
# 1. 의존성 설치
npm install

# 2. 환경변수 설정
cp .env.example .env.local
# .env.local 파일을 편집하여 Firebase 설정 입력

# 3. 개발 서버 실행
npm run dev

# 브라우저에서 http://localhost:3000 자동 오픈
```

> ⚠️ **중요**: 백엔드 서버(8080 포트)가 실행 중이어야 합니다!

---

## 📚 문서

- **백엔드 개발자용 가이드**: [`../docs/BACKEND_DEVELOPER_FRONTEND_GUIDE.md`](docs/BACKEND_DEVELOPER_FRONTEND_GUIDE.md)
  - 프로젝트 구조, API 연동, Firebase FCM, PWA 설정 등 상세 설명
  - **백엔드 개발자라면 이 문서를 먼저 읽으세요!**

- **아이콘 생성 가이드**: [`public/ICONS_README.md`](public/ICONS_README.md)
  - PWA 아이콘 생성 및 관리

---

## 🛠 기술 스택

- **프레임워크**: React 18 + TypeScript
- **빌드 도구**: Vite 6
- **UI**: Radix UI + TailwindCSS
- **푸시 알림**: Firebase Cloud Messaging (FCM)
- **지도**: Leaflet
- **PWA**: Service Worker + Manifest

---

## 📁 주요 디렉토리

```
front/
├── public/              # 정적 파일 (아이콘, manifest 등)
├── src/
│   ├── api/            # 백엔드 API 호출
│   ├── components/     # React 컴포넌트
│   ├── config/         # Firebase 등 설정
│   ├── hooks/          # 커스텀 Hook
│   ├── types/          # TypeScript 타입
│   └── utils/          # 유틸리티 함수
├── .env.example        # 환경변수 템플릿
└── vite.config.ts      # Vite 설정
```

---

## 📝 주요 명령어

```bash
npm run dev              # 개발 서버 실행
npm run build            # 프로덕션 빌드
npm run generate-icons   # PWA 아이콘 생성
```

---

## 🔧 환경변수 설정

`.env.local` 파일에 다음 값을 설정하세요:

```env
# Firebase 설정 (Firebase Console에서 확인)
VITE_FIREBASE_API_KEY=
VITE_FIREBASE_AUTH_DOMAIN=
VITE_FIREBASE_PROJECT_ID=
VITE_FIREBASE_STORAGE_BUCKET=
VITE_FIREBASE_MESSAGING_SENDER_ID=
VITE_FIREBASE_APP_ID=
VITE_FIREBASE_MEASUREMENT_ID=
VITE_FIREBASE_VAPID_KEY=
```

> `.env.local`은 절대 Git에 커밋하지 마세요!

---

## 🏗 빌드 및 배포

### 프로덕션 빌드
```bash
npm run build
```

빌드 결과물은 `dist/` 폴더에 생성됩니다.

### 배포 옵션

**Option 1: Nginx**
```nginx
server {
    root /var/www/beach-complex/front/dist;
    location / {
        try_files $uri /index.html;
    }
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

**Option 2: Spring Boot 내장**
- `dist/` 내용을 `src/main/resources/static/`으로 복사

---

## 🔥 Firebase FCM 설정

1. Firebase Console (https://console.firebase.google.com) 접속
2. 프로젝트 생성
3. 프로젝트 설정 > 웹 앱 추가
4. Cloud Messaging > 웹 푸시 인증서 생성
5. 설정값을 `.env.local`에 복사

자세한 내용은 [백엔드 개발자용 가이드](docs/BACKEND_DEVELOPER_FRONTEND_GUIDE.md#6-firebase-fcm-설정) 참고

---

## 🎨 PWA 아이콘

아이콘은 자동으로 생성됩니다:

```bash
npm run generate-icons
```

아이콘 디자인을 변경하려면 `public/logo.svg`를 수정한 후 위 명령어를 실행하세요.

---

## 🐛 트러블슈팅

### Service Worker 업데이트 안 됨
1. F12 > Application > Service Workers
2. "Unregister" 클릭
3. Ctrl+Shift+R (강제 새로고침)

### API 호출 실패
1. 백엔드 서버(8080) 실행 확인
2. `vite.config.ts`의 proxy 설정 확인
3. F12 > Network 탭에서 요청 URL 확인

### Firebase 초기화 실패
1. `.env.local` 파일 존재 및 내용 확인
2. 개발 서버 재시작 (`npm run dev`)
3. `public/firebase-config.js` 생성 확인

더 많은 문제 해결 방법은 [백엔드 개발자용 가이드](docs/BACKEND_DEVELOPER_FRONTEND_GUIDE.md#9-트러블슈팅) 참고

---

## 🧪 로컬 테스트 시 참고사항

해수욕장 상태를 테스트하려면:

```bash
docker compose exec postgres psql -U beach -d beach_complex -c \
"UPDATE beaches SET status='normal' WHERE code='HAEUNDAE';
 UPDATE beaches SET status='free'   WHERE code='SONGJEONG';
 UPDATE beaches SET status='busy'   WHERE code='GWANGALLI';"
```

---

## 👥 팀 공유

팀원이 처음 세팅할 때:

```bash
# 1. 클론
git clone <repository>
cd Beach_complex/front

# 2. 설치
npm install

# 3. 환경변수 복사
cp .env.example .env.local
# .env.local 편집 (팀 리더에게 Firebase 설정 요청)

# 4. 실행
npm run dev
```

---

## 📖 더 알아보기

- [백엔드 개발자용 가이드](docs/BACKEND_DEVELOPER_FRONTEND_GUIDE.md) - **필독!**
- [React 공식 문서](https://react.dev/learn)
- [Vite 공식 문서](https://vitejs.dev/)
- [Firebase FCM 문서](https://firebase.google.com/docs/cloud-messaging/js/client)

---

**프로젝트**: 비치체크 (Beach Check)
**기술 스택**: React + TypeScript + Vite + Firebase
**팀**: 백엔드 개발자들이 프론트엔드도 관리 중
**원본 디자인**: [Figma](https://www.figma.com/design/a3ofEvvgfRDF8TI3YaG6dA)