# Firebase 설정 가이드

## 📋 Firebase 프로젝트 설정

### 1️⃣ Firebase Console에서 프로젝트 생성

1. [Firebase Console](https://console.firebase.google.com) 접속
2. "프로젝트 추가" 클릭
3. 프로젝트 이름 입력: `beach-complex` (또는 원하는 이름)
4. Google Analytics 설정 (선택사항)
5. 프로젝트 생성 완료

---

### 2️⃣ 웹 앱 추가

1. Firebase 프로젝트 → 프로젝트 설정(⚙️)
2. "앱 추가" → "웹" 선택
3. 앱 닉네임 입력: `Beach Complex Web`
4. Firebase Hosting 설정 (선택사항)
5. 앱 등록 완료

---

### 3️⃣ Cloud Messaging 설정

1. 프로젝트 설정 → "Cloud Messaging" 탭
2. "웹 푸시 인증서" 섹션
3. "키 생성" 버튼 클릭
4. **VAPID 키 복사** → 프론트엔드 설정에 사용

---

### 4️⃣ 서비스 계정 키 다운로드 (Backend용)

1. 프로젝트 설정 → "서비스 계정" 탭
2. "새 비공개 키 생성" 버튼 클릭
3. JSON 파일 다운로드
4. 파일명을 `firebase-service-account.json`으로 변경
5. 로컬 개발 시 다음 위치에 저장:
   ```
   src/main/resources/firebase-service-account.json
   ```

⚠️ **중요**: 이 파일은 `.gitignore`에 포함되어 있으므로 Git에 커밋되지 않습니다!

### 운영/배포 환경 권장 방식

- 운영 컨테이너에서는 classpath 파일 대신 환경변수로 주입하는 방식을 권장합니다.
- 지원 방식:
  - `APP_FIREBASE_CREDENTIALS_JSON_BASE64`: 서비스 계정 JSON 전체를 base64 인코딩한 값
  - `APP_FIREBASE_CREDENTIALS_PATH`: 컨테이너 내부에서 읽을 수 있는 JSON 파일 경로
- 우선순위:
  1. `APP_FIREBASE_CREDENTIALS_JSON_BASE64`
  2. `APP_FIREBASE_CREDENTIALS_PATH`
  3. `src/main/resources/firebase-service-account.json`

예시:
```bash
base64 -w 0 firebase-service-account.json
```

EC2 env 예시:
```env
APP_FIREBASE_ENABLED=true
APP_FIREBASE_CREDENTIALS_JSON_BASE64=<base64-encoded-json>
```

---

## 🔧 Backend 설정 완료 확인

### 파일 위치 확인
```
Beach_complex/
├── src/
│   └── main/
│       └── resources/
│           ├── firebase-service-account.json      ✅ 실제 키 파일
│           └── firebase-service-account.json.example  (예시)
```

### 애플리케이션 실행
```bash
./gradlew bootRun
```

로그에서 다음 메시지 확인:
```
FirebaseApp initialized successfully
```

---

## 📝 Frontend 설정

### Firebase Config 정보 복사

프로젝트 설정에서 다음 정보 확인:
```javascript
const firebaseConfig = {
  apiKey: "AIzaSy...",
  authDomain: "beach-complex.firebaseapp.com",
  projectId: "beach-complex",
  storageBucket: "beach-complex.firebasestorage.app",
  messagingSenderId: "921237508805",
  appId: "1:921237508805:web:...",
  measurementId: "G-..."
};
```

### VAPID 키 확인
Cloud Messaging → 웹 푸시 인증서에서 생성한 키

---

## 🔒 보안 주의사항

### ❌ 절대 하지 말 것
- `firebase-service-account.json` 파일을 Git에 커밋
- 서비스 계정 키를 public repository에 공개
- API 키를 코드에 하드코딩

### ✅ 올바른 방법
- 서비스 계정 키는 로컬에만 보관
- 환경변수 또는 Secret Manager 사용 (프로덕션)
- `.gitignore`에 추가되어 있는지 확인

---

## 🚨 문제 해결

### Firebase 초기화 실패
```
Failed to initialize Firebase: firebase-service-account.json
```

**해결 방법:**
1. 로컬 개발이면 `src/main/resources/firebase-service-account.json` 파일 존재 확인
2. 운영 환경이면 `APP_FIREBASE_CREDENTIALS_JSON_BASE64` 또는 `APP_FIREBASE_CREDENTIALS_PATH` 설정 확인
3. JSON 파일 형식이 올바른지 확인
4. 파일 경로 사용 시 컨테이너에서 읽을 수 있는 권한인지 확인

### FCM 발송 실패
```
FirebaseMessagingException: Invalid registration token
```

**해결 방법:**
1. 프론트엔드에서 올바른 FCM 토큰을 전송했는지 확인
2. VAPID 키가 올바른지 확인
3. 토큰이 만료되지 않았는지 확인

---

## 📚 참고 자료

- [Firebase Admin SDK 설정](https://firebase.google.com/docs/admin/setup)
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- [프로젝트 구현 가이드](../../docs/guides/notification-implementation-guide.md)

---

**작성일**: 2026-01-17
