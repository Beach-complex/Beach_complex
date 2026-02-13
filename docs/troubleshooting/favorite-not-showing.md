# 트러블슈팅: 찜 목록이 프론트엔드에 표시되지 않는 문제

**컴포넌트:** api

**작성일:** 2025-12-30

## 📋 문제 상황

### 증상
- DB에는 찜 목록 데이터가 정상적으로 저장되어 있음
- 프론트엔드에서 찜한 해수욕장에 하트 아이콘이 채워지지 않음
- 찜 필터를 클릭해도 아무 항목도 표시되지 않음

### 발생 일시
2025-12-30

### 영향 범위
- 사용자가 로그인한 상태에서 찜한 해수욕장 목록을 볼 수 없음
- 찜 기능은 작동하지만 UI에 반영되지 않음

---

## 🔍 원인 분석

### 1. 백엔드 문제

#### 문제점
`BeachController`와 `BeachService`가 사용자 인증 정보를 받지 않아서, **찜 여부를 확인할 수 없었음**

#### 세부 사항

**BeachController.java (수정 전)**
```java
@GetMapping
public ResponseEntity<List<BeachDto>> findAll(@Valid BeachSearchRequestDto request) {
    // ...
    if (request.hasCompleteRadiusParams()) {
        return ResponseEntity.ok(
            beachService.findNearby(request.lon(), request.lat(), request.radiusKm())
            // ❌ User 파라미터가 없음!
        );
    }
    // ...
}
```

**BeachService.java (수정 전)**
```java
public List<BeachDto> findNearby(double longitude, double latitude, double radiusKm) {
    double radiusMeters = radiusKm * 1000;

    return beachRepository.findBeachesWithinRadius(longitude, latitude, radiusMeters)
            .stream()
            .map(BeachDto::from)  // ❌ 항상 isFavorite = false
            .toList();
}
```

**BeachDto.java**
```java
public static BeachDto from(Beach beach) {
    return from(beach, false);  // ❌ 기본값이 항상 false
}
```

**문제의 흐름:**
1. 클라이언트가 `/api/beaches?lat=...&lon=...` 요청
2. `BeachController`가 요청을 받지만 `User` 정보를 받지 않음
3. `BeachService`가 `BeachDto.from(beach)`로 변환
4. 모든 해수욕장의 `isFavorite`가 `false`로 설정됨
5. 클라이언트는 찜 정보가 없는 데이터를 받음

### 2. 프론트엔드 문제

#### 문제점
`/api/beaches` API 호출 시 **Authorization 헤더를 전송하지 않아서**, 백엔드가 사용자를 식별할 수 없었음

#### 세부 사항

**App.tsx (수정 전)**
```typescript
useEffect(() => {
    // ...
    fetch(`/api/beaches?${params}`, { signal: controller.signal })
    // ❌ Authorization 헤더가 없음!
      .then((res) => {
        // ...
      })
}, [coords, isAuthenticated]);
// ❌ authState?.accessToken이 의존성 배열에 없음
```

**문제의 흐름:**
1. 사용자가 로그인하여 `authState`에 토큰 저장됨
2. 위치 정보 로드 후 `/api/beaches` API 호출
3. **요청 헤더에 `Authorization: Bearer <token>` 없음**
4. Spring Security가 요청을 익명 사용자로 처리
5. `@AuthenticationPrincipal User user`가 `null`이 됨
6. 백엔드가 찜 여부를 확인할 수 없음

**Spring Security의 동작:**
```
요청 → Spring Security Filter Chain
     → Authorization 헤더 확인
     → 헤더 없음 → 익명 사용자로 처리
     → @AuthenticationPrincipal User = null
```

---

## ✅ 해결 방법

### 1. 백엔드 수정

#### 1-1. BeachService.java 수정

**위치:** `src/main/java/com/beachcheck/service/BeachService.java`

**변경 내용:**

```java
// ✅ User 파라미터 추가
public List<BeachDto> findNearby(double longitude, double latitude, double radiusKm, User user) {
    double radiusMeters = radiusKm * 1000;
    List<Beach> beaches = beachRepository.findBeachesWithinRadius(longitude, latitude, radiusMeters);
    return toDtoList(beaches, user);  // ✅ 찜 여부 포함
}

// ✅ 헬퍼 메서드 추가 - 효율적인 찜 여부 확인
private List<BeachDto> toDtoList(List<Beach> beaches, User user) {
    if (user != null) {
        // 한 번의 DB 쿼리로 모든 찜 ID 조회 (N+1 문제 방지)
        Set<UUID> favoriteIds = favoriteService.getFavoriteBeachIds(user);
        return beaches.stream()
                .map(beach -> BeachDto.from(beach, favoriteIds.contains(beach.getId())))
                .toList();
    } else {
        // 비로그인 사용자는 모두 false
        return beaches.stream()
                .map(BeachDto::from)
                .toList();
    }
}

// ✅ 다른 메서드들도 동일하게 수정
public List<BeachDto> findAll(User user) {
    return toDtoList(beachRepository.findAll(), user);
}

public List<BeachDto> search(String q, String tag, User user) {
    // ...
    return toDtoList(rows, user);
}

public BeachDto findByCode(String code, User user) {
    Beach beach = beachRepository.findByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Beach with code " + code + " not found"));
    return toDto(beach, user);
}

private BeachDto toDto(Beach beach, User user) {
    if (user != null) {
        boolean isFavorite = favoriteService.isFavorite(user, beach.getId());
        return BeachDto.from(beach, isFavorite);
    }
    return BeachDto.from(beach);
}
```

**개선 사항:**
- `toDtoList()` 메서드로 N+1 쿼리 문제 방지
  - 기존: 해수욕장 개수만큼 `isFavorite` 확인 쿼리 실행
  - 개선: 한 번의 쿼리로 모든 찜 ID 조회 후 메모리에서 비교

#### 1-2. BeachController.java 수정

**위치:** `src/main/java/com/beachcheck/controller/BeachController.java`

**변경 내용:**

```java
// ✅ import 추가
import com.beachcheck.domain.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/beaches")
@Validated
public class BeachController {

    // ✅ @AuthenticationPrincipal User user 파라미터 추가
    @GetMapping
    public ResponseEntity<List<BeachDto>> findAll(
            @Valid BeachSearchRequestDto request,
            @AuthenticationPrincipal User user  // ✅ Spring Security에서 자동 주입
    ) {
        request.validateRadiusParams();

        if (request.hasCompleteRadiusParams()) {
            return ResponseEntity.ok(
                    beachService.findNearby(request.lon(), request.lat(), request.radiusKm(), user)
            );
        }

        if (request.q() != null || request.tag() != null) {
            return ResponseEntity.ok(beachService.search(request.q(), request.tag(), user));
        }

        return ResponseEntity.ok(beachService.findAll(user));
    }

    @GetMapping("/{code}")
    public ResponseEntity<BeachDto> findByCode(
            @PathVariable @NotBlank String code,
            @AuthenticationPrincipal User user  // ✅ 추가
    ) {
        return ResponseEntity.ok(beachService.findByCode(code, user));
    }
}
```

**`@AuthenticationPrincipal` 동작 원리:**
1. Spring Security가 요청 헤더의 `Authorization: Bearer <token>` 확인
2. JWT 토큰 검증 및 파싱
3. 토큰에서 사용자 정보 추출
4. `User` 엔티티를 컨트롤러 메서드에 자동 주입
5. 토큰이 없거나 유효하지 않으면 `user = null`

### 2. 프론트엔드 수정

#### 2-1. App.tsx 수정

**위치:** `front/src/App.tsx`

**변경 내용:**

```typescript
useEffect(() => {
    if (!coords) {
      return;
    }

    const controller = new AbortController();
    setIsLoadingBeaches(true);
    setBeachError(null);

    const params = new URLSearchParams({
      lat: coords.lat.toString(),
      lon: coords.lng.toString(),
      radiusKm: '50'
    });

    // ✅ 디버깅 로그 추가
    console.log('🔍 [Beach API] 요청 시작:', `/api/beaches?${params}`);
    console.log('🔍 [Beach API] 인증 상태:', isAuthenticated ? '로그인' : '비로그인');
    console.log('🔍 [Beach API] 현재 토큰:', authState?.accessToken ? `${authState.accessToken.substring(0, 20)}...` : 'null');

    // ✅ Authorization 헤더 추가
    fetch(`/api/beaches?${params}`, {
      signal: controller.signal,
      headers: authState?.accessToken ? {
        'Authorization': `Bearer ${authState.accessToken}`  // ✅ 핵심!
      } : {}
    })
      .then((res) => {
        console.log('🔍 [Beach API] 응답 상태:', res.status);
        if (!res.ok) {
          throw new Error(`API Error: ${res.status}`);
        }
        return res.json();
      })
      .then((data: Beach[]) => {
        // ✅ 디버깅 로그 추가
        console.log('🔍 [Beach API] 받은 데이터:', data);
        console.log('🔍 [Beach API] isFavorite=true인 항목:', data.filter(b => b.isFavorite));

        setBeaches(data);
        if (isAuthenticated) {
          const serverFavIds = data.filter(b => b.isFavorite).map(b => b.id);
          console.log('🔍 [Beach API] 서버에서 받은 찜 ID 목록:', serverFavIds);
          setFavoriteBeaches(prev => {
            const newFavs = Array.from(new Set([...prev, ...serverFavIds]));
            console.log('🔍 [Beach API] 업데이트된 찜 목록:', newFavs);
            return newFavs;
          });
        }
        if (data.length > 0) {
          setLastSelectedBeach((previous) => previous ?? data[0] ?? null);
        }
        console.log(`✅ ${data.length}개 해수욕장 발견 (반경 50km)`);
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }

        if (error && typeof error === 'object' && 'name' in error && (error as { name: string }).name === 'AbortError') {
          return;
        }

        const message = error instanceof Error ? error.message : '해수욕장 정보를 불러오지 못했습니다.';
        console.error('🔍 [Beach API] 에러:', error);
        setBeachError(message);
      })
      .finally(() => {
        setIsLoadingBeaches(false);
      });

    return () => controller.abort();
}, [coords, isAuthenticated, authState?.accessToken]);  // ✅ 의존성 추가
```

**주요 변경사항:**

1. **Authorization 헤더 추가 (187-192번째 줄)**
   ```typescript
   headers: authState?.accessToken ? {
     'Authorization': `Bearer ${authState.accessToken}`
   } : {}
   ```
   - `authState?.accessToken`이 있으면 헤더에 추가
   - 없으면 빈 객체 (비로그인 사용자)

2. **의존성 배열 수정 (237번째 줄)**
   ```typescript
   }, [coords, isAuthenticated, authState?.accessToken]);
   ```
   - `authState?.accessToken` 추가
   - 로그인 후 토큰이 생성되면 자동으로 API 재호출

3. **디버깅 로그 추가**
   - API 요청/응답 추적
   - 찜 데이터 확인
   - 문제 재발 시 빠른 원인 파악 가능

---

## 🔍 디버깅 방법

### 브라우저 개발자 도구 활용

#### 1. Console 탭
```
F12 → Console 탭

[예상 출력]
🔍 [Beach API] 요청 시작: /api/beaches?lat=35.1796&lon=129.0756&radiusKm=50
🔍 [Beach API] 인증 상태: 로그인
🔍 [Beach API] 현재 토큰: eyJhbGciOiJIUzI1NiIs...
🔍 [Beach API] 응답 상태: 200
🔍 [Beach API] 받은 데이터: (5) [{…}, {…}, {…}, {…}, {…}]
  ▶ 0: {id: "...", name: "해운대", isFavorite: true, ...}
  ▶ 1: {id: "...", name: "광안리", isFavorite: false, ...}
🔍 [Beach API] isFavorite=true인 항목: (1) [{…}]
🔍 [Beach API] 서버에서 받은 찜 ID 목록: ['...']
🔍 [Beach API] 업데이트된 찜 목록: ['...']
✅ 5개 해수욕장 발견 (반경 50km)
```

#### 2. Network 탭
```
F12 → Network 탭 → Fetch/XHR 필터

[/api/beaches 요청 클릭]

Headers:
  Request Headers:
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  ✅

Response:
  [
    {
      "id": "uuid-here",
      "code": "HAEUNDAE",
      "name": "해운대",
      "status": "normal",
      "latitude": 35.1587,
      "longitude": 129.1603,
      "updatedAt": "2025-12-30T...",
      "tag": null,
      "isFavorite": true  ✅
    }
  ]
```

### 체크리스트

- [ ] Console에 "🔍 [Beach API] 인증 상태: 로그인" 표시됨
- [ ] Console에 "🔍 [Beach API] 현재 토큰: ..." 에 토큰 값 있음
- [ ] Network 탭에서 Request Headers에 `Authorization: Bearer ...` 있음
- [ ] Network 탭에서 Response에 `"isFavorite": true` 항목이 있음
- [ ] Console에 "🔍 [Beach API] isFavorite=true인 항목" 배열이 비어있지 않음
- [ ] UI에서 찜한 해수욕장에 보라색 하트 아이콘이 채워짐

---

## 📊 데이터 흐름 비교

### 수정 전 (문제 상황)

```
[클라이언트]
  로그인 ✅ → authState.accessToken 저장됨
  ↓
  fetch('/api/beaches?lat=...&lon=...')
  ❌ Authorization 헤더 없음
  ↓
[백엔드]
  Spring Security Filter
  → 헤더 없음 → 익명 사용자로 처리
  ↓
  BeachController.findAll(request)
  → @AuthenticationPrincipal User user = null ❌
  ↓
  BeachService.findNearby(lon, lat, radius)
  → User 파라미터 없음 ❌
  ↓
  BeachDto.from(beach)
  → isFavorite = false (기본값) ❌
  ↓
  Response: [{..., isFavorite: false}, {..., isFavorite: false}]
  ↓
[클라이언트]
  모든 해수욕장의 isFavorite가 false
  → 하트 아이콘이 채워지지 않음 ❌
```

### 수정 후 (정상 작동)

```
[클라이언트]
  로그인 ✅ → authState.accessToken 저장됨
  ↓
  fetch('/api/beaches?lat=...&lon=...', {
    headers: {
      Authorization: `Bearer ${token}` ✅
    }
  })
  ↓
[백엔드]
  Spring Security Filter
  → Authorization 헤더 확인 ✅
  → JWT 토큰 검증 ✅
  → User 엔티티 추출 ✅
  ↓
  BeachController.findAll(request, user)
  → @AuthenticationPrincipal User user ✅
  ↓
  BeachService.findNearby(lon, lat, radius, user)
  → User 파라미터 전달 ✅
  ↓
  favoriteService.getFavoriteBeachIds(user)
  → Set<UUID> favoriteIds = {...} ✅
  ↓
  BeachDto.from(beach, favoriteIds.contains(beach.getId()))
  → isFavorite = true/false (실제 찜 여부) ✅
  ↓
  Response: [{..., isFavorite: true}, {..., isFavorite: false}]
  ↓
[클라이언트]
  isFavorite가 true인 항목만 하트 아이콘 채움 ✅
```

---

## 💡 핵심 교훈

### 1. JWT 인증의 필수 요소
- **백엔드**: `@AuthenticationPrincipal`로 사용자 정보 받기
- **프론트엔드**: `Authorization: Bearer <token>` 헤더 전송
- **양쪽 모두 구현되어야 인증이 작동함**

### 2. Spring Security의 동작 원리
```
요청 → Security Filter Chain
     → JwtAuthenticationFilter (커스텀)
     → JWT 검증 및 Authentication 객체 생성
     → SecurityContext에 저장
     → @AuthenticationPrincipal이 SecurityContext에서 꺼내옴
```

### 3. N+1 쿼리 문제 방지
```java
// ❌ 나쁜 예: 해수욕장마다 DB 쿼리
beaches.stream()
    .map(beach -> {
        boolean isFavorite = favoriteService.isFavorite(user, beach.getId());
        // → 5개 해수욕장이면 5번의 SELECT 쿼리!
        return BeachDto.from(beach, isFavorite);
    })

// ✅ 좋은 예: 한 번의 쿼리로 모든 찜 ID 조회
Set<UUID> favoriteIds = favoriteService.getFavoriteBeachIds(user);  // 1번의 SELECT
beaches.stream()
    .map(beach -> BeachDto.from(beach, favoriteIds.contains(beach.getId())))
    // → 메모리에서 Set.contains() 체크 (O(1))
```

### 4. React useEffect 의존성 배열
```typescript
// ❌ 토큰 변경 시 재호출 안 됨
useEffect(() => {
  fetch('/api/beaches', {
    headers: { Authorization: `Bearer ${authState?.accessToken}` }
  })
}, [coords, isAuthenticated])

// ✅ 토큰 변경 시 자동 재호출
useEffect(() => {
  // ...
}, [coords, isAuthenticated, authState?.accessToken])
```

### 5. 디버깅 로그의 중요성
- 문제 발생 시 원인 파악 시간 단축
- API 요청/응답 추적
- 프로덕션 환경에서는 로그 레벨 조정 필요

---

## 🎯 재발 방지 체크리스트

### 새로운 인증 필요 API 추가 시

#### 백엔드
- [ ] `@AuthenticationPrincipal User user` 파라미터 추가
- [ ] `user != null` 체크
- [ ] 사용자별 데이터 필터링 로직 구현
- [ ] N+1 쿼리 방지 (Batch 조회 또는 JOIN)

#### 프론트엔드
- [ ] `Authorization: Bearer ${token}` 헤더 추가
- [ ] `authState?.accessToken` 의존성 배열에 추가
- [ ] 401 Unauthorized 에러 처리
- [ ] 토큰 만료 시 재로그인 유도

#### 테스트
- [ ] 로그인 상태에서 API 호출 테스트
- [ ] 비로그인 상태에서 API 호출 테스트
- [ ] 브라우저 Network 탭에서 헤더 확인
- [ ] 응답 데이터에 사용자별 정보 포함 확인

---

## 📚 참고 자료

### Spring Security
- `@AuthenticationPrincipal`: Spring Security가 제공하는 어노테이션
- JWT 필터 체인: `SecurityConfig`에서 설정
- `SecurityContextHolder`: 현재 인증 정보 저장소

### React useEffect
- 의존성 배열: 값이 변경되면 effect 재실행
- 토큰 변경 감지: `authState?.accessToken` 추가 필요

### HTTP 헤더
- `Authorization: Bearer <token>`: RFC 6750 OAuth 2.0 표준
- JWT: JSON Web Token, 상태 없는 인증 방식

---

## 📝 버전 정보

- **발견일**: 2025-12-30
- **해결일**: 2025-12-30
- **작성자**: Claude Sonnet 4.5
- **검증**: 실제 브라우저 테스트 완료

---

## ✅ 결론

이 문제는 **백엔드와 프론트엔드 양쪽의 인증 통합 부족**으로 발생했습니다.

**백엔드**는 사용자 정보를 받을 준비가 되어있지 않았고,
**프론트엔드**는 인증 토큰을 전송하지 않았습니다.

양쪽을 모두 수정하여 **JWT 기반 인증이 완전히 작동**하도록 했으며,
이제 사용자별 찜 목록이 정상적으로 표시됩니다.
