/**
 * Firebase Cloud Messaging Service Worker
 *
 * Why: 백그라운드(브라우저 닫힌 상태 또는 다른 탭)에서도 푸시 알림을 수신하기 위함
 * Policy:
 *   - iOS Safari는 PWA로 설치해야만 동작 (iOS 16.4+)
 *   - Android Chrome은 브라우저에서 바로 동작
 */

// Firebase SDK 로드 (compat 버전 사용 - Service Worker에서 호환성 높음)
importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-messaging-compat.js');

// Firebase 환경변수 로드 (Vite 빌드 시 자동 생성됨)
importScripts('/firebase-config.js');

// Firebase 초기화 (FIREBASE_CONFIG는 firebase-config.js에서 주입됨)
firebase.initializeApp(self.FIREBASE_CONFIG);

const messaging = firebase.messaging();

// 백그라운드 메시지 수신
messaging.onBackgroundMessage((payload) => {
    console.log('[Service Worker] 백그라운드 메시지 수신:', payload);

    const notificationTitle = payload.notification?.title || '새 알림';
    const notificationOptions = {
        body: payload.notification?.body || '',
        icon: '/assets/icons/icon-192x192.png',  // 알림 아이콘
        badge: '/assets/icons/badge-72x72.png',  // (선택) 작은 뱃지 아이콘
        tag: 'beach-notification',               // 같은 tag면 알림이 덮어씌워짐(중복 알림 방지)
        requireInteraction: true,                // iOS PWA에서 중요: 사용자가 닫을 때까지 표시
        data: payload.data                       // 클릭 시 활용할 데이터(예: URL)
    };

    // 브라우저 알림 표시
    return self.registration.showNotification(notificationTitle, notificationOptions);
});

// 알림 클릭 이벤트 처리
self.addEventListener('notificationclick', (event) => {
    console.log('[Service Worker] 알림 클릭됨:', event.notification);

    event.notification.close();  // 알림 닫기

    // TODO: 알림 데이터에서 URL 추출하여 특정 페이지로 이동(나중에 백엔드에서 기능별 알림 발송 구현할때 할것)

    // 앱 열기 (또는 특정 페이지로 이동)
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            // 현재 앱의 도메인 (예: http://localhost:3000/)
            const urlToOpen = new URL('/', self.location.origin).href;

            // 이미 열려있는 탭이 있으면 해당 탭 포커스
            // 같은 도메인의 어떤 페이지든 열려있으면 새 탭 생성 방지
            for (const client of clientList) {
                if (client.url.startsWith(self.location.origin) && 'focus' in client) {
                    return client.focus();
                }
            }

            // 열려있는 탭 없으면 새 탭 열기
            if (clients.openWindow) {
                return clients.openWindow(urlToOpen);
            }
        })
    );
});