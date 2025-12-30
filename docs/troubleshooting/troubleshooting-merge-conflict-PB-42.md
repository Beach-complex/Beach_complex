# Git Merge Conflict 트러블슈팅 가이드 - PB-42 Favorite 기능

## 📋 목차
1. [문제 상황](#문제-상황)
2. [충돌 원인 분석](#충돌-원인-분석)
3. [해결 과정](#해결-과정)
4. [중복 커밋 문제](#중복-커밋-문제)
5. [교훈 및 Best Practices](#교훈-및-best-practices)
6. [참고 자료](#참고-자료)

---

## 문제 상황

### 발생 시점
- **브랜치**: `feat/PB-42-favorite-personalization`
- **작업**: 찜 기능 서버 연동 (AuthView 통합 + 서버 API 연동)
- **트리거**: `git pull origin feat/PB-42-favorite-personalization`

### 에러 메시지
```bash
Auto-merging front/src/App.tsx
CONFLICT (content): Merge conflict in front/src/App.tsx
Auto-merging front/src/api/favorites.ts
CONFLICT (content): Merge conflict in front/src/api/favorites.ts
Automatic merge failed; fix conflicts and then commit the result.
```

### 충돌 파일
1. **`front/src/api/favorites.ts`** - 토큰 인증 방식 충돌
2. **`front/src/App.tsx`** - Auth 통합 vs 서버 API 연동 충돌

---

## 충돌 원인 분석

### 1. favorites.ts 충돌

**Current (HEAD - 로컬 작업)**:
```typescript
import { loadAuth } from '@/utils/auth';

function getAuthToken(): string | null {
  const auth = loadAuth();
  return auth?.accessToken ?? null;
}
```
- ✅ `loadAuth()` 사용 (올바른 방법)
- ✅ `beachcheck_auth` 키에서 JSON 파싱 후 accessToken 추출
- ✅ 디버그 로그 포함

**Incoming (원격 버전)**:
```typescript
function getAuthToken(): string | null {
  return localStorage.getItem('accessToken');
}
```
- ❌ 직접 `localStorage.getItem('accessToken')` 사용
- ❌ 실제로는 `beachcheck_auth` 키에 JSON으로 저장되어 있어서 토큰을 못 찾음
- ❌ 결과: 항상 `null` 반환 → 403 Forbidden 에러

**근본 원인**:
- 토큰 저장 방식: `localStorage.setItem('beachcheck_auth', JSON.stringify({ accessToken, user, ... }))`
- 잘못된 접근: `localStorage.getItem('accessToken')` → `null`
- 올바른 접근: `loadAuth()?.accessToken`

### 2. App.tsx 충돌

**Current (로컬 - 서버 API 연동)**:
```typescript
// 서버에서 찜 목록 로드
useEffect(() => {
  if (!isAuthenticated) {
    setFavoriteBeaches([]);
    return;
  }

  favoritesApi.getMyFavorites()
    .then((favorites) => {
      const favoriteIds = favorites.map(beach => beach.id);
      setFavoriteBeaches(favoriteIds);
    })
    .catch((error) => {
      console.error('Failed to load favorites from server:', error);
      // localStorage fallback
    });
}, [isAuthenticated]);

// 서버 API로 찜 토글
const toggleFavoriteById = async (beachId: string) => {
  if (!requireAuth('찜 기능을 사용하려면 로그인하세요.')) {
    return;
  }

  const result = await favoritesApi.toggleFavorite(beachId);
  // state 업데이트...
};
```

**Incoming (원격 - localStorage 기반)**:
```typescript
// localStorage에서 찜 목록 로드
useEffect(() => {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    // localStorage에서 찜 로드
  }
}, []);

// localStorage 기반 찜 토글
const toggleFavorite = (beachId: string, e: React.MouseEvent) => {
  e.stopPropagation();
  const token = localStorage.getItem('accessToken');

  if (!token) {
    // localStorage에 저장
    setFavoriteBeaches(prev => /* ... */);
  } else {
    // 서버 API 호출
  }
};
```

**차이점**:
- Current: `isAuthenticated` (AuthState) 기반, requireAuth 패턴
- Incoming: `localStorage.getItem('accessToken')` 직접 체크
- Current: AuthView 통합 완료
- Incoming: AuthView 미통합

---

## 해결 과정

### Phase 1: 충돌 마커 이해하기

충돌이 발생하면 파일에 다음과 같은 마커가 생깁니다:

```
<<<<<<< HEAD (Current Change)
로컬에서 작업한 코드
=======
원격에서 가져온 코드
>>>>>>> 커밋해시 (Incoming Change)
```

### Phase 2: VS Code Merge Conflict UI 사용

VS Code는 충돌 발생 시 다음 옵션을 제공합니다:
- **Accept Current Change**: 로컬 버전 선택
- **Accept Incoming Change**: 원격 버전 선택
- **Accept Both Changes**: 둘 다 포함
- **Compare Changes**: 비교 뷰

### Phase 3: favorites.ts 해결

**결정**: Accept Current Change

**이유**:
1. `loadAuth()` 방식이 올바름
2. 디버그 로그가 포함되어 있어 문제 추적 용이
3. Incoming 버전은 토큰을 찾지 못하는 버그가 있음

**적용**:
```typescript
// ✅ 최종 선택
import { loadAuth } from '@/utils/auth';

function getAuthToken(): string | null {
  const auth = loadAuth();
  return auth?.accessToken ?? null;
}

// 디버그 로그 포함
async toggleFavorite(beachId: string) {
  const token = getAuthToken();
  console.log('🔍 [toggleFavorite] token:', token ? `${token.substring(0, 20)}...` : 'null');
  console.log('🔍 [toggleFavorite] URL:', `${API_BASE}/${beachId}/toggle`);

  const res = await fetch(`${API_BASE}/${beachId}/toggle`, {
    method: 'PUT',
    headers: createAuthHeaders()
  });

  console.log('🔍 [toggleFavorite] response status:', res.status);

  if (!res.ok) {
    const errorText = await res.text();
    console.error('🔍 [toggleFavorite] error response:', errorText);
    alert(`❌ 찜 실패: ${res.status}\n\n응답: ${errorText.substring(0, 200)}`);
    throw new Error(`Failed to toggle favorite: ${res.status} ${res.statusText}`);
  }

  const data = await res.json();
  console.log('🔍 [toggleFavorite] success:', data);
  return data;
}
```

### Phase 4: App.tsx 해결 (복잡)

App.tsx는 여러 부분에서 충돌이 발생했습니다:

#### 충돌 1: Import 구문
```typescript
// Current (로컬)
import { AuthView } from './components/AuthView';
import { clearAuth, loadAuth, type StoredAuth } from './utils/auth';

// Incoming (원격)
import { fetchBeaches } from './data/beaches';
```

**결정**: Accept Current (AuthView 통합이 필요)

#### 충돌 2: State 선언
```typescript
// Current (로컬)
const [authState, setAuthState] = useState<StoredAuth | null>(() => loadAuth());
const [authEntryMode, setAuthEntryMode] = useState<'login' | 'signup'>('login');
const [authNotice, setAuthNotice] = useState<string | null>(null);

// Incoming (원격)
// Auth 관련 state 없음
```

**결정**: Accept Current (Auth 기능 필요)

#### 충돌 3: 찜 목록 로드
```typescript
// Current (로컬)
useEffect(() => {
  if (!isAuthenticated) {
    setFavoriteBeaches([]);
    return;
  }

  favoritesApi.getMyFavorites()
    .then((favorites) => {
      const favoriteIds = favorites.map(beach => beach.id);
      setFavoriteBeaches(favoriteIds);
    })
    .catch((error) => {
      console.error('Failed to load favorites from server:', error);
      // localStorage fallback
      if (typeof window !== 'undefined') {
        const savedFavorites = localStorage.getItem('beachcheck_favorites');
        if (savedFavorites) {
          try {
            const parsed = JSON.parse(savedFavorites);
            if (Array.isArray(parsed)) {
              setFavoriteBeaches(parsed.map((id: unknown) => String(id)));
            }
          } catch (error) {
            console.warn('Failed to parse stored favorites', error);
          }
        }
      }
    });
}, [isAuthenticated]);

// Incoming (원격)
useEffect(() => {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    // 비로그인: localStorage에서 로드
    const savedFavorites = localStorage.getItem('beachcheck_favorites');
    // ...
  } else {
    // 로그인: 서버에서 로드
    favoritesApi.getMyFavorites()
    // ...
  }
}, []);
```

**결정**: Accept Current
- `isAuthenticated` 사용이 더 명확
- AuthState 기반 관리
- 의존성 배열에 `isAuthenticated` 포함 (로그인/로그아웃 시 자동 재실행)

#### 충돌 4: 찜 토글 함수
```typescript
// Current (로컬)
const toggleFavoriteById = async (beachId: string) => {
  if (!requireAuth('찜 기능을 사용하려면 로그인하세요.')) {
    return;
  }

  try {
    const result = await favoritesApi.toggleFavorite(beachId);

    setFavoriteBeaches(prev => {
      if (result.isFavorite) {
        return [...prev, beachId];
      } else {
        return prev.filter(id => id !== beachId);
      }
    });

    setBeaches(prev => prev.map(beach =>
      beach.id === beachId ? { ...beach, isFavorite: result.isFavorite } : beach
    ));
  } catch (error) {
    console.error('Failed to toggle favorite:', error);
    alert('찜 상태 변경에 실패했습니다. 다시 시도해주세요.');
  }
};

// Incoming (원격)
const toggleFavorite = async (beachId: string, e: React.MouseEvent) => {
  e.stopPropagation();
  const token = localStorage.getItem('accessToken');

  if (!token) {
    // 비로그인: localStorage 업데이트
    setFavoriteBeaches(prev => /* ... */);
    return;
  }

  // 로그인: 서버 API 호출
  try {
    const result = await favoritesApi.toggleFavorite(beachId);
    // ...
  } catch (error) {
    console.error('Failed to toggle favorite:', error);
    alert('찜 상태 변경에 실패했습니다. 다시 시도해주세요.');
  }
};
```

**결정**: Accept Current
- `requireAuth` 패턴이 더 일관성 있음
- 로그인 안 하면 자동으로 로그인 화면 이동
- 비로그인 사용자는 찜 기능 사용 불가 (요구사항 명확)

#### 충돌 5: View 렌더링
```typescript
// Current (로컬)
if (currentView === 'auth') {
  return (
    <AuthView
      initialMode={authEntryMode}
      notice={authNotice}
      onClose={() => {
        setCurrentView('mypage');
        setSelectedBeach(null);
        setActiveTab('mypage');
        setAuthNotice(null);
      }}
      onAuthSuccess={handleAuthSuccess}
    />
  );
}

// Incoming (원격)
// AuthView 렌더링 없음
```

**결정**: Accept Current (AuthView 필수)

### Phase 5: 통합 버전 작성

충돌이 너무 많아서 Claude가 직접 깨끗한 통합 버전을 작성했습니다:

**통합된 기능**:
1. ✅ AuthView 통합 (팀원 버전)
2. ✅ requireAuth 패턴
3. ✅ handleAuthRequest, handleAuthSuccess, handleSignOut
4. ✅ authState, authEntryMode, authNotice
5. ✅ loadAuth()로 토큰 가져오기
6. ✅ favoritesApi.getMyFavorites() - 서버에서 찜 로드
7. ✅ favoritesApi.toggleFavorite() - 서버에서 찜 토글
8. ✅ 디버그 로그 포함

**최종 동작**:
- 비로그인: 찜 버튼 클릭 시 로그인 화면으로 이동
- 로그인: 서버 API로 찜 관리

### Phase 6: 빌드 테스트

```bash
cd front && npm run build
```

**결과**:
```
✓ 3195 modules transformed.
✓ built in 5.81s
```

✅ 빌드 성공!

---

## 중복 커밋 문제

### 문제 발견

PR에서 같은 커밋이 두 번씩 나타남:
```
feat: UserFavoriteRepository 구현 (7시간 전)
feat: UserFavoriteService 구현 (7시간 전)
feat: UserFavoriteController 구현 (6시간 전)
refactor: BeachService 수정 (6시간 전)
...
feat: UserFavoriteRepository 구현 (1시간 전)  # 중복!
feat: UserFavoriteService 구현 (1시간 전)    # 중복!
feat: UserFavoriteController 구현 (1시간 전)  # 중복!
refactor: BeachService 수정 (1시간 전)        # 중복!
```

### 원인 분석

```bash
git log --oneline -25
```

결과:
```
3820b9e Merge branch 'feat/PB-42-favorite-personalization' of ...
33d6590 feat: 프론트엔드 favorite 기능 서버 연동 구현
a092e0f refactor: BeachDto 메서드 구조 변화에 따른 수정
8dede72 refactor: BeachService 수정
d367dc6 feat: UserFavoriteController 구현
3683648 feat: UserFavoriteService 구현
fc19592 feat: UserFavoriteRepository 구현
07e656a feat: UserFavorite 객체 구현
1963ab0 refactor: SecurityConfig 수정
47dc914 refactor: 'Beach'객체에서 사용하지 않는 'is_favorite'필드 제거
4d1cecd feat: DB 스키마 마이그레이션 V6__add_user_favorites 추가
616fbdd feat: 프론트엔드 favorite 기능 서버 연동 구현  # 중복!
cbab79b refactor: BeachDto 메서드 구조 변화에 따른 수정  # 중복!
4ea916e refactor: BeachService 수정                      # 중복!
6edbd38 feat: UserFavoriteController 구현               # 중복!
b974cc0 feat: UserFavoriteService 구현                  # 중복!
d15f409 feat: UserFavoriteRepository 구현              # 중복!
```

**원인**:
- 로컬과 원격이 갈라진 상태에서 merge를 함
- 같은 작업이 다른 커밋 해시로 두 번 들어감

### 해결 방안

#### 옵션 1: GitHub에서 Squash and Merge (권장)

**장점**:
- 가장 간단함
- PR 머지 시 모든 커밋이 하나로 합쳐짐
- 중복 커밋 자동 제거
- main 브랜치 히스토리 깔끔

**방법**:
1. PR 생성
2. "Squash and merge" 버튼 클릭
3. 커밋 메시지 작성:
```
feat: 찜 기능 서버 연동 및 Auth 통합 (#105)

- UserFavorite 엔티티, Repository, Service, Controller 구현
- BeachService에 UserFavoriteService 통합
- BeachDto에 isFavorite 필드 추가
- favorites API 클라이언트 구현 (loadAuth 사용)
- App.tsx에 Auth 통합 및 requireAuth 패턴 적용
- DB 마이그레이션 V6 추가
```

#### 옵션 2: Interactive Rebase로 정리

**장점**:
- 히스토리를 깔끔하게 정리
- 중복 커밋 제거

**단점**:
- 충돌이 다시 발생할 수 있음
- 시간이 오래 걸림

**방법**:
```bash
# 1. 충돌 전 커밋으로 돌아가기
git log --oneline -30
# f480b28 (Merge pull request #104) 찾기

# 2. Interactive rebase 시작
git rebase -i f480b28

# 3. 에디터에서 중복 커밋 제거
# pick → drop 또는 삭제

# 4. 충돌 발생 시 해결 후
git add .
git rebase --continue

# 5. 강제 푸시
git push -f origin feat/PB-42-favorite-personalization
```

**문제점**: 시도했으나 중복 커밋이 너무 많아서 계속 충돌 발생

#### 옵션 3: Reset --soft 후 재커밋 (간단 + 깔끔)

**방법**:
```bash
# 1. 현재 변경사항을 임시 저장
git reset --soft f480b28

# 2. 모든 변경사항이 staged 상태가 됨
git status

# 3. 하나의 깔끔한 커밋으로 만들기
git commit -m "feat: 찜 기능 서버 연동 및 Auth 통합

- UserFavorite 엔티티, Repository, Service, Controller 구현
- BeachService에 UserFavoriteService 통합
- BeachDto에 isFavorite 필드 추가
- favorites API 클라이언트 구현 (loadAuth 사용)
- App.tsx에 Auth 통합 및 requireAuth 패턴 적용
- DB 마이그레이션 V6 추가"

# 4. Force push
git push -f origin feat/PB-42-favorite-personalization
```

**장점**:
- 매우 간단
- 충돌 없음
- 히스토리 깔끔

**단점**:
- Force push 필요 (하지만 피처 브랜치라 괜찮음)

### 최종 선택

**권장**: 옵션 1 (Squash and Merge)
- PR 머지 시 자동으로 정리됨
- Force push 불필요
- 가장 안전

**대안**: 옵션 3 (Reset --soft)
- 지금 당장 히스토리를 깔끔하게 하고 싶을 때
- 빠르고 간단

---

## 교훈 및 Best Practices

### 1. 토큰 저장 방식 통일

**문제**:
- `localStorage.setItem('beachcheck_auth', JSON.stringify(...))`로 저장
- `localStorage.getItem('accessToken')`로 읽기 시도

**해결**:
```typescript
// ❌ 잘못된 방법
const token = localStorage.getItem('accessToken'); // null

// ✅ 올바른 방법
import { loadAuth } from '@/utils/auth';
const auth = loadAuth();
const token = auth?.accessToken ?? null;
```

**Best Practice**:
- 토큰 접근은 항상 `loadAuth()` 유틸리티 사용
- 직접 localStorage 접근 금지
- 중앙화된 인증 관리

### 2. Merge Conflict 해결 전략

**체크리스트**:
1. ✅ 충돌 파일 목록 확인: `git status`
2. ✅ 각 충돌 이해하기: `git diff`
3. ✅ Current vs Incoming 비교
4. ✅ 비즈니스 로직 우선순위 결정
5. ✅ 통합 버전 작성 고려
6. ✅ 빌드 테스트: `npm run build`
7. ✅ 기능 테스트 (로그인, 찜 추가/제거)

### 3. requireAuth 패턴 사용

**Before (비일관적)**:
```typescript
const token = localStorage.getItem('accessToken');
if (!token) {
  // 비로그인 처리
  setFavoriteBeaches(prev => /* ... */);
} else {
  // 로그인 처리
  await favoritesApi.toggleFavorite(beachId);
}
```

**After (일관적)**:
```typescript
const toggleFavoriteById = async (beachId: string) => {
  if (!requireAuth('찜 기능을 사용하려면 로그인하세요.')) {
    return; // 자동으로 로그인 화면 이동
  }

  // 로그인한 사용자만 여기 도달
  const result = await favoritesApi.toggleFavorite(beachId);
  // ...
};
```

**장점**:
- 코드 중복 제거
- 일관된 사용자 경험
- 로그인 필요 메시지 중앙 관리

### 4. 디버깅 로그 전략

**프로덕션 배포 전에는 유지**:
```typescript
async toggleFavorite(beachId: string) {
  const token = getAuthToken();
  console.log('🔍 [toggleFavorite] beachId:', beachId);
  console.log('🔍 [toggleFavorite] token:', token ? `${token.substring(0, 20)}...` : 'null');
  console.log('🔍 [toggleFavorite] URL:', `${API_BASE}/${beachId}/toggle`);

  const res = await fetch(/* ... */);

  console.log('🔍 [toggleFavorite] response status:', res.status);

  if (!res.ok) {
    const errorText = await res.text();
    console.error('🔍 [toggleFavorite] error response:', errorText);
    // ...
  }

  const data = await res.json();
  console.log('🔍 [toggleFavorite] success:', data);
  return data;
}
```

**배포 후 제거 또는 환경변수로 제어**:
```typescript
const DEBUG = import.meta.env.DEV;

if (DEBUG) {
  console.log('🔍 [toggleFavorite] token:', token);
}
```

### 5. Git Workflow

**권장 플로우**:
```bash
# 1. 작업 전 최신 코드 받기
git pull origin feat/PB-42-favorite-personalization

# 2. 충돌 발생 시
git status  # 충돌 파일 확인

# 3. 충돌 해결
# - VS Code UI 사용
# - 또는 수동으로 <<<<<<< ======= >>>>>>> 마커 제거

# 4. 해결 후 확인
npm run build  # 빌드 테스트

# 5. 커밋
git add .
git commit -m "Merge: 충돌 해결 - [설명]"

# 6. 푸시
git push origin feat/PB-42-favorite-personalization
```

### 6. 중복 커밋 예방

**원인**:
- 로컬과 원격이 갈라진 상태에서 merge

**예방**:
```bash
# ❌ 나쁜 습관
git commit -m "feat: something"
git pull  # 충돌 발생 + 중복 커밋 가능성
git push

# ✅ 좋은 습관
git pull  # 먼저 최신 코드 받기
# 충돌 해결
git commit -m "feat: something"
git push
```

**또는 rebase 사용**:
```bash
git pull --rebase origin feat/PB-42-favorite-personalization
# 충돌 해결
git add .
git rebase --continue
git push
```

---

## 참고 자료

### 커밋 히스토리

**Before (중복)**:
```
3820b9e Merge branch 'feat/PB-42-favorite-personalization' of ...
33d6590 feat: 프론트엔드 favorite 기능 서버 연동 구현
...
616fbdd feat: 프론트엔드 favorite 기능 서버 연동 구현  # 중복!
...
```

**After (Squash and Merge)**:
```
abc1234 feat: 찜 기능 서버 연동 및 Auth 통합 (#105)
```

### 주요 변경사항

**favorites.ts**:
```diff
- import { Beach } from '@/types/beach';
+ import { Beach } from '@/types/beach';
+ import { loadAuth } from '@/utils/auth';

  function getAuthToken(): string | null {
-   return localStorage.getItem('accessToken');
+   const auth = loadAuth();
+   return auth?.accessToken ?? null;
  }
```

**App.tsx**:
```diff
+ import { AuthView } from './components/AuthView';
+ import { clearAuth, loadAuth, type StoredAuth } from './utils/auth';

+ const [authState, setAuthState] = useState<StoredAuth | null>(() => loadAuth());
+ const [authEntryMode, setAuthEntryMode] = useState<'login' | 'signup'>('login');
+ const [authNotice, setAuthNotice] = useState<string | null>(null);

+ const requireAuth = (notice: string) => {
+   if (authState) {
+     return true;
+   }
+   handleAuthRequest('login', notice);
+   return false;
+ };

  const toggleFavoriteById = async (beachId: string) => {
+   if (!requireAuth('찜 기능을 사용하려면 로그인하세요.')) {
+     return;
+   }

    try {
      const result = await favoritesApi.toggleFavorite(beachId);
      // ...
    } catch (error) {
      console.error('Failed to toggle favorite:', error);
      alert('찜 상태 변경에 실패했습니다. 다시 시도해주세요.');
    }
  };
```

### 관련 문서

- [docs/app-tsx-merge-plan.md](../app-tsx-merge-plan.md) - App.tsx 병합 계획
- [docs/frontend-favorite-integration-guide.md](../frontend-favorite-integration-guide.md) - 프론트엔드 연동 가이드
- [docs/pr-template-PB-42.md](../pr-template-PB-42.md) - PR 템플릿
- [docs/깃충돌.txt](../깃충돌.txt) - 이전 리베이스 충돌 기록

---

## 타임라인

| 시간 | 이벤트 | 상태 |
|------|--------|------|
| T-7h | 백엔드 구현 완료 (UserFavorite, Service, Controller) | ✅ |
| T-5h | 프론트엔드 구현 완료 (favorites.ts, App.tsx 서버 연동) | ✅ |
| T-1h | 팀원이 AuthView 통합 작업 완료 후 푸시 | ✅ |
| T-0 | `git pull` → Merge conflict 발생 | ❌ |
| T+10m | favorites.ts 충돌 해결 (Accept Current - loadAuth) | ✅ |
| T+30m | App.tsx 충돌 다수 발견 | ⚠️ |
| T+1h | App.tsx 수동 병합 시도 (여러 Accept Current/Incoming 선택) | 🔄 |
| T+1.5h | Claude가 통합 버전 작성 | ✅ |
| T+1.5h | `npm run build` 성공 | ✅ |
| T+2h | 중복 커밋 문제 발견 | ⚠️ |
| T+2h | 해결 방안 검토 (Squash and Merge vs Reset --soft) | 📋 |

---

## 결론

### 성공한 점
1. ✅ 복잡한 Merge Conflict 해결
2. ✅ Auth 통합 + 서버 API 연동 완료
3. ✅ requireAuth 패턴 적용
4. ✅ 빌드 성공
5. ✅ 디버그 로그로 문제 추적 가능

### 개선이 필요한 점
1. ⚠️ 중복 커밋 정리 필요 → Squash and Merge 권장
2. ⚠️ Git workflow 개선 (pull --rebase 사용 고려)
3. ⚠️ 팀원과 작업 범위 사전 조율 (AuthView vs API 연동)

### 다음 단계
1. PR 생성
2. Squash and Merge로 중복 커밋 정리
3. 기능 테스트 (로그인, 찜 추가/제거)
4. 배포 전 디버그 로그 제거 또는 환경변수로 제어

---

**작성일**: 2024-12-29
**작성자**: GunwooPar + Claude Code
**관련 이슈**: PB-42
**관련 PR**: #105