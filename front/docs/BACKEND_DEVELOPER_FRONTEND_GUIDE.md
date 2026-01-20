# 백엔드 개발자를 위한 프론트엔드 가이드

> **대상**: 백엔드 개발자가 프론트엔드 코드를 이해하고 유지보수할 수 있도록 작성된 문서

## 목차
1. [프로젝트 개요](#1-프로젝트-개요)
2. [개발 환경 설정](#2-개발-환경-설정)
3. [프로젝트 구조](#3-프로젝트-구조)
4. [빌드 및 배포](#4-빌드-및-배포)
5. [API 연동 방식](#5-api-연동-방식)
6. [Firebase FCM 설정](#6-firebase-fcm-설정)
7. [PWA 설정](#7-pwa-설정)
8. [자주 하는 작업](#8-자주-하는-작업)
9. [트러블슈팅](#9-트러블슈팅)

---

## 1. 프로젝트 개요

### 기술 스택
- **프레임워크**: React 18 (TypeScript)
- **빌드 도구**: Vite 6
- **UI 라이브러리**: Radix UI, TailwindCSS
- **상태 관리**: React Hooks (useState, useEffect)
- **HTTP 클라이언트**: Fetch API (네이티브)
- **푸시 알림**: Firebase Cloud Messaging (FCM)
- **지도**: Leaflet (react-leaflet)

### 프론트엔드와 백엔드 관계
```
┌─────────────────┐          ┌─────────────────┐
│  React Frontend │  ──HTTP─>│  Spring Backend │
│  (Vite Dev:3000)│  <─JSON──│   (Port: 8080)  │
└─────────────────┘          └─────────────────┘
         │
         │ (Service Worker)
         ▼
┌─────────────────┐
│  Firebase FCM   │  (푸시 알림)
└─────────────────┘
```

---

## 2. 개발 환경 설정

### 필수 설치
```bash
# Node.js 20.x 이상 필요
node --version  # v20.10.0 이상

# 의존성 설치
cd front
npm install
```

### 환경변수 설정 (.env.local)
```bash
# front/.env.local 파일 생성 (절대 커밋하지 말 것!)
cp .env.example .env.local
```

**.env.local 예시**:
```env
# Firebase 설정 (Firebase Console에서 복사)
VITE_FIREBASE_API_KEY=AIzaSy...
VITE_FIREBASE_AUTH_DOMAIN=beach-complex.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=beach-complex
VITE_FIREBASE_STORAGE_BUCKET=beach-complex.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=123456789
VITE_FIREBASE_APP_ID=1:123456789:web:abc123
VITE_FIREBASE_MEASUREMENT_ID=G-ABC123
VITE_FIREBASE_VAPID_KEY=BDq3... (Web Push Certificates)
```

> ⚠️ **중요**: `.env.local`은 절대 Git에 커밋하지 마세요! (`.gitignore`에 이미 추가됨)

### 개발 서버 실행
```bash
cd front
npm run dev

# 브라우저에서 http://localhost:3000 자동 오픈
```

백엔드 서버(8080)가 실행 중이어야 API 호출이 정상 작동합니다.

---

## 3. 프로젝트 구조

```
front/
├── public/                      # 정적 파일 (빌드 시 그대로 복사됨)
│   ├── manifest.webmanifest    # PWA 설정
│   ├── firebase-messaging-sw.js # Service Worker (FCM)
│   ├── firebase-config.js      # 🔴 자동 생성 (커밋 X)
│   ├── logo.svg                # 앱 로고
│   └── icon-*.png              # PWA 아이콘들
│
├── src/
│   ├── main.tsx                # 진입점 (Service Worker 등록)
│   ├── App.tsx                 # 메인 컴포넌트
│   ├── index.css               # 전역 CSS
│   │
│   ├── components/             # UI 컴포넌트
│   │   ├── BeachCard.tsx       # 해수욕장 카드
│   │   ├── BeachDetailView.tsx # 상세 페이지
│   │   ├── MyPageView.tsx      # 마이페이지
│   │   └── ui/                 # 재사용 UI (Button, Dialog 등)
│   │
│   ├── config/
│   │   └── firebase.ts         # Firebase 초기화 및 FCM 함수
│   │
│   ├── api/                    # 백엔드 API 호출
│   │   ├── beaches.ts          # 해수욕장 API
│   │   └── favorites.ts        # 찜 API
│   │
│   ├── hooks/                  # 커스텀 Hook
│   │   └── useUserLocation.ts  # 사용자 위치 가져오기
│   │
│   ├── types/                  # TypeScript 타입 정의
│   │   ├── beach.ts
│   │   └── auth.ts
│   │
│   └── utils/                  # 유틸리티 함수
│       └── auth.ts             # 인증 관련 (localStorage)
│
├── vite.config.ts              # Vite 설정 (프록시, 플러그인)
├── tsconfig.json               # TypeScript 설정
├── package.json                # 의존성 및 스크립트
├── .env.example                # 환경변수 템플릿
└── .env.local                  # 🔴 실제 환경변수 (커밋 X)
```

### 핵심 파일 설명

| 파일 | 역할 | 백엔드 개발자가 수정할 가능성 |
|------|------|------------------------------|
| `src/api/*.ts` | 백엔드 API 호출 로직 | ⭐⭐⭐ 높음 |
| `src/types/*.ts` | API 응답 타입 정의 | ⭐⭐⭐ 높음 |
| `vite.config.ts` | API 프록시 설정 | ⭐⭐ 중간 |
| `.env.local` | 환경변수 | ⭐⭐ 중간 |
| `src/components/*` | UI 컴포넌트 | ⭐ 낮음 |

---

## 4. 빌드 및 배포

### 개발 빌드
```bash
npm run dev  # 개발 서버 (Hot Reload)
```

### 프로덕션 빌드
```bash
npm run build

# 빌드 결과: front/dist/ 폴더에 생성됨
```

### 빌드 산출물
```
front/dist/
├── index.html           # 진입 HTML
├── assets/              # JS, CSS (해시 포함)
│   ├── index-abc123.js
│   └── index-def456.css
├── manifest.webmanifest # PWA 설정
├── firebase-*.js        # Service Worker
└── *.png                # 아이콘들
```

### 배포 방법

#### 옵션 1: Nginx로 서빙(업계표준, 프론트 백엔드 도커 분리)
```nginx
server {
    listen 80;
    server_name beach.example.com;
    root /var/www/beach-complex/front/dist;

    location / {
        try_files $uri $uri/ /index.html;  # SPA 라우팅
    }

    location /api {
        proxy_pass http://localhost:8080;  # 백엔드 프록시
    }
}
```

#### 옵션 2: Spring Boot에서 직접 서빙(간편함, 백엔드 도커에다가 포함)
```java
// src/main/resources/application.yml
spring:
  web:
    resources:
      static-locations: classpath:/static/,file:front/dist/
```

빌드 후 `dist/` 내용을 `src/main/resources/static/`으로 복사

---

## 5. API 연동 방식

### API 호출 구조
```typescript
// src/api/beaches.ts 예시
export async function fetchBeaches(lat: number, lon: number): Promise<Beach[]> {
  const params = new URLSearchParams({
    lat: lat.toString(),
    lon: lon.toString(),
    radiusKm: '50'
  });

  const response = await fetch(`/api/beaches?${params}`, {
    headers: {
      'Authorization': `Bearer ${accessToken}`,  // 인증 토큰
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.status}`);
  }

  return response.json();
}
```

### Vite 프록시 설정 (개발 환경)
```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 백엔드 서버
        changeOrigin: true
      }
    }
  }
});
```

**작동 방식**:
- 프론트: `fetch('/api/beaches')` 호출
- Vite: `http://localhost:8080/api/beaches`로 프록시
- CORS 문제 없음!

### API 응답 타입 정의
```typescript
// src/types/beach.ts
export interface Beach {
  id: string;
  code: string;
  name: string;
  latitude: number;
  longitude: number;
  isFavorite?: boolean;  // 백엔드 BeachDto와 일치해야 함!
}
```

> ⚠️ **중요**: 백엔드 DTO 변경 시 프론트 타입도 함께 수정하세요!

---

## 6. Firebase FCM 설정

### FCM 개요
Firebase Cloud Messaging = **푸시 알림 시스템**

```
사용자 브라우저 ─(FCM 토큰)→ 백엔드 저장
                              │
                              ▼
백엔드 ─(알림 발송 요청)→ Firebase
                              │
                              ▼
                        사용자 브라우저에 알림 표시
```

### 주요 파일

#### 1. firebase.ts (프론트엔드)
```typescript
// 알림 권한 요청 + FCM 토큰 발급
const token = await requestNotificationPermission();

// 백엔드에 토큰 저장
await saveFcmTokenToBackend(token, accessToken);
```

#### 2. firebase-messaging-sw.js (Service Worker)
- 백그라운드 알림 수신
- 알림 클릭 처리
- **환경변수는 firebase-config.js에서 자동 주입됨**

#### 3. firebase-config.js (자동 생성)
- Vite 플러그인이 `.env.local`을 읽어 자동 생성
- **절대 커밋하지 마세요!**

### 백엔드에서 알림 발송

백엔드에서는 저장된 FCM 토큰으로 알림을 보냅니다:

```java
// Spring Boot + Firebase Admin SDK
public void sendNotification(String fcmToken, String title, String body) {
    Message message = Message.builder()
        .setToken(fcmToken)
        .setNotification(Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build())
        .build();

    FirebaseMessaging.getInstance().send(message);
}
```

### Firebase 프로젝트 설정

1. Firebase Console (https://console.firebase.google.com) 접속
2. 프로젝트 생성
3. 프로젝트 설정 > 웹 앱 추가
4. Firebase SDK 설정값 복사 → `.env.local`에 붙여넣기
5. Cloud Messaging > 웹 푸시 인증서 생성 → VAPID Key 복사

---

## 7. PWA 설정

### PWA란?
Progressive Web App = **설치 가능한 웹앱**

- Android/iOS에서 "홈 화면에 추가" 가능
- 오프라인 동작 (Service Worker)
- 푸시 알림 수신

### 주요 파일

#### 1. manifest.webmanifest
```json
{
  "name": "비치체크",
  "short_name": "비치체크",
  "start_url": "/",
  "display": "standalone",  // 독립 앱처럼 실행
  "theme_color": "#007DFC",
  "icons": [ /* ... */ ]
}
```

#### 2. index.html (iOS 전용 태그)
```html
<!-- iOS PWA 활성화 -->
<meta name="apple-mobile-web-app-capable" content="yes" />
<link rel="apple-touch-icon" href="/apple-touch-icon.png" />
```

#### 3. 아이콘 생성
```bash
# 아이콘 재생성
npm run generate-icons

# logo.svg → icon-*.png 자동 생성됨
```

### Service Worker 등록
```typescript
// src/main.tsx
navigator.serviceWorker.register('/firebase-messaging-sw.js');
```

---

## 8. 자주 하는 작업

### 8.1 새로운 API 엔드포인트 추가

**백엔드**:
```java
@GetMapping("/api/beaches/{id}")
public BeachDetailDto getBeachDetail(@PathVariable String id) {
    // ...
}
```

**프론트엔드**:
```typescript
// 1. 타입 정의 (src/types/beach.ts)
export interface BeachDetail extends Beach {
  facilities: string[];
  openingHours: string;
}

// 2. API 함수 추가 (src/api/beaches.ts)
export async function fetchBeachDetail(id: string): Promise<BeachDetail> {
  const response = await fetch(`/api/beaches/${id}`);
  return response.json();
}

// 3. 컴포넌트에서 사용
const detail = await fetchBeachDetail(beachId);
```

### 8.2 환경변수 추가

```bash
# 1. .env.example 업데이트 (팀원 공유용)
echo "VITE_NEW_API_KEY=" >> .env.example

# 2. .env.local에 실제 값 추가 (본인만)
echo "VITE_NEW_API_KEY=abc123" >> .env.local

# 3. TypeScript 타입 추가 (src/vite-env.d.ts)
interface ImportMetaEnv {
  readonly VITE_NEW_API_KEY: string
}

# 4. 코드에서 사용
const apiKey = import.meta.env.VITE_NEW_API_KEY;
```

### 8.3 의존성 추가

```bash
# 일반 의존성
npm install axios

# 개발 의존성 (빌드 도구 등)
npm install --save-dev @types/node

# 버전 고정
npm install react@18.3.1
```

> ⚠️ package-lock.json은 반드시 커밋하세요!

### 8.4 CORS 에러 해결

**개발 환경**: vite.config.ts에서 프록시 설정
```typescript
proxy: {
  '/api': { target: 'http://localhost:8080' }
}
```

**프로덕션**: 백엔드에서 CORS 허용
```java
@CrossOrigin(origins = "https://beach.example.com")
```

---

## 9. 트러블슈팅

### 9.1 빌드 실패

**증상**: `npm run build` 에러
```bash
# 해결 1: node_modules 재설치
rm -rf node_modules package-lock.json
npm install

# 해결 2: 캐시 삭제
rm -rf .vite dist
npm run build
```

### 9.2 Service Worker 업데이트 안 됨

**증상**: 코드 변경했는데 반영 안 됨

```bash
# 해결: 브라우저에서
1. F12 (개발자 도구)
2. Application 탭
3. Service Workers
4. "Unregister" 클릭
5. 페이지 새로고침 (Ctrl+Shift+R)
```

### 9.3 환경변수 안 읽힘

**증상**: `import.meta.env.VITE_XXX`가 undefined

```bash
# 체크리스트:
1. .env.local 파일 존재 확인
2. 변수명이 VITE_ 로 시작하는지 확인
3. 개발 서버 재시작 (npm run dev)
4. 빌드 시점에 주입되므로 변경 후 재빌드 필요
```

### 9.4 API 호출 실패

**증상**: Network Error 또는 CORS

```bash
# 디버깅:
1. F12 > Network 탭에서 실제 요청 URL 확인
2. 백엔드 서버(8080) 실행 중인지 확인
3. vite.config.ts 프록시 설정 확인
4. 백엔드 로그에서 요청 도착했는지 확인
```

### 9.5 Firebase 초기화 실패

**증상**: "Firebase Messaging 초기화 실패"

```bash
# 해결:
1. .env.local에 Firebase 설정 확인
2. public/firebase-config.js 생성되었는지 확인
3. 개발 서버 재시작
4. Firebase Console에서 Web Push Certificate 활성화 확인
```

---

## 10. 유용한 명령어 모음

```bash
# 개발
npm run dev                    # 개발 서버
npm run build                  # 프로덕션 빌드
npm run generate-icons         # PWA 아이콘 재생성

# 디버깅
npm list firebase              # 설치된 버전 확인
npm outdated                   # 업데이트 가능한 패키지 확인
npm audit                      # 보안 취약점 검사

# 정리
rm -rf node_modules dist .vite # 완전 초기화
npm install                    # 의존성 재설치
```

---

## 11. 백엔드 개발자가 알아야 할 React 기초

### 컴포넌트 = 함수
```typescript
// 컴포넌트는 그냥 함수입니다
function BeachCard({ beach }: { beach: Beach }) {
  return <div>{beach.name}</div>;
}

// Java로 비유하면:
// public String renderBeachCard(Beach beach) {
//     return "<div>" + beach.getName() + "</div>";
// }
```

### useState = 변수 선언 + setter
```typescript
const [count, setCount] = useState(0);

// Java로 비유하면:
// private int count = 0;
// public void setCount(int value) { this.count = value; }
```

### useEffect = 생명주기
```typescript
useEffect(() => {
  // 컴포넌트가 화면에 나타날 때 실행
  fetchData();
}, []);  // [] = 의존성 배열

// Java로 비유하면:
// @PostConstruct
// public void init() { fetchData(); }
```

### API 호출
```typescript
const response = await fetch('/api/beaches');
const data = await response.json();

// Java로 비유하면:
// ResponseEntity<List<Beach>> response = restTemplate.getForEntity(...);
// List<Beach> data = response.getBody();
```

---

## 12. 체크리스트 (커밋 전)

- [ ] `.env.local` 파일 커밋하지 않았는가?
- [ ] `firebase-config.js` 커밋하지 않았는가?
- [ ] `package-lock.json` 포함했는가?
- [ ] TypeScript 에러 없는가? (IDE에서 빨간 줄 확인)
- [ ] 백엔드 DTO 변경 시 프론트 타입도 수정했는가?
- [ ] 로컬에서 빌드 성공하는가? (`npm run build`)

---

## 13. 참고 자료

- **React 공식 문서**: https://react.dev/learn
- **Vite 공식 문서**: https://vitejs.dev/
- **Firebase FCM**: https://firebase.google.com/docs/cloud-messaging/js/client
- **PWA 가이드**: https://web.dev/progressive-web-apps/
- **TypeScript Handbook**: https://www.typescriptlang.org/docs/

---

## 14. 팀원에게 공유할 때

이 문서를 읽고 다음을 실행해보세요:

```bash
# 1. 프로젝트 클론
git clone <repository>
cd Beach_complex/front

# 2. 의존성 설치
npm install

# 3. 환경변수 설정
cp .env.example .env.local
# .env.local을 편집기로 열어 Firebase 설정 입력

# 4. 개발 서버 실행
npm run dev

# 5. 백엔드 서버도 실행 (8080 포트)
cd ..
./gradlew bootRun

# 6. 브라우저에서 http://localhost:3000 접속
```

질문이 있으면 이 문서를 먼저 확인하고, 해결 안 되면 팀원에게 물어보세요!

---

**작성일**: 2026-01-20
**작성자**: 개발팀
**문서 버전**: 1.0