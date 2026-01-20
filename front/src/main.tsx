
  import { createRoot } from "react-dom/client";
  import App from "./App.tsx";
  import "./index.css";
  import 'leaflet/dist/leaflet.css';

  createRoot(document.getElementById("root")!).render(<App />);

  // Service Worker 등록 (PWA 및 FCM 푸시 알림용)
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker
        .register('/firebase-messaging-sw.js')
        .then((registration) => {
          console.log('Service Worker 등록 성공:', registration.scope);
        })
        .catch((error) => {
          console.error('Service Worker 등록 실패:', error);
        });
    });
  }