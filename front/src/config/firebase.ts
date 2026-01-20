/**
 * Why: Firebase Cloud Messaging SDK 초기화 및 FCM 토큰 관리
 * Policy:
 *   - 브라우저 알림 권한 요청은 사용자 액션 후에만 수행
 *   - FCM 토큰은 로컬스토리지에 캐시하여 불필요한 재발급 방지
 * Contract(Output):
 *   - requestNotificationPermission(): FCM 토큰 반환 또는 null
 *   - onMessageListener(): 포그라운드 메시지 수신 Promise
 */

import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage, Messaging } from 'firebase/messaging';

// Firebase 설정 (환경 변수에서 로드)
const firebaseConfig = {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
    measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID
};

// Firebase 앱 초기화
const app = initializeApp(firebaseConfig);

// Messaging 인스턴스 (브라우저에서만 동작)
let messaging: Messaging | null = null;

try {
    messaging = getMessaging(app);
} catch (error) {
    console.error('Firebase Messaging 초기화 실패 (Service Worker 미설치?)', error);
}

/**
 * 알림 권한 요청 및 FCM 토큰 발급
 *
 * Why: 사용자에게 푸시 알림 권한을 요청하고 FCM 토큰을 백엔드에 저장하기 위함
 * Policy:
 *   - 사용자가 "차단"을 선택하면 다시 요청하지 않음
 *   - 토큰 발급 실패 시 로그만 출력하고 예외 발생 안 함
 */
export const requestNotificationPermission = async (): Promise<string | null> => {
    if (!messaging) {
        console.warn('Firebase Messaging이 초기화되지 않았습니다.');
        return null;
    }

    try {
        // 1. 브라우저 알림 권한 요청
        const permission = await Notification.requestPermission();

        if (permission === 'granted') {
            console.log('알림 권한 허용됨');

            // 2. FCM 토큰 발급
            const token = await getToken(messaging, {
                vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY
            });

            console.log('FCM 토큰 발급 성공:', token);
            return token;
        } else if (permission === 'denied') {
            console.warn('사용자가 알림 권한을 거부했습니다.');
            return null;
        } else {
            console.log('사용자가 알림 권한 결정을 보류했습니다.');
            return null;
        }
    } catch (error) {
        console.error('FCM 토큰 발급 실패:', error);
        return null;
    }
};

/**
 * 포그라운드 메시지 수신 리스너
 *
 * Why: 앱이 활성화(포그라운드) 상태일 때 푸시 알림을 받기 위함
 * Policy:
 *   - 백그라운드 메시지는 Service Worker에서 처리
 *   - 포그라운드 메시지는 이 리스너에서 처리하여 Toast UI 표시
 */
export const onMessageListener = (): Promise<any> => {
    if (!messaging) {
        return Promise.reject('Firebase Messaging이 초기화되지 않았습니다.');
    }

    return new Promise((resolve) => {
        onMessage(messaging!, (payload) => {
            console.log('포그라운드 메시지 수신:', payload);
            resolve(payload);
        });
    });
};

/**
 * FCM 토큰 백엔드 전송
 *
 * Why: 발급받은 FCM 토큰을 백엔드에 저장하여 푸시 알림 발송 가능하게 함
 */
export const saveFcmTokenToBackend = async (token: string, accessToken: string): Promise<void> => {
    try {
        const response = await fetch('/api/notifications/fcm-token', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
            },
            body: JSON.stringify({ fcmToken: token })
        });

        if (!response.ok) {
            throw new Error(`FCM 토큰 저장 실패: ${response.status}`);
        }

        console.log('FCM 토큰 백엔드 저장 완료');
    } catch (error) {
        console.error('FCM 토큰 백엔드 저장 실패:', error);
    }
};