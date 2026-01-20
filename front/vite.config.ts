
  import { defineConfig } from 'vite';
  import react from '@vitejs/plugin-react-swc';
  import path from 'path';
  import fs from 'fs';

  // Firebase 환경변수를 Service Worker에서 사용할 수 있도록 파일로 생성하는 플러그인
  function firebaseConfigPlugin() {
    return {
      name: 'firebase-config-plugin',
      // 개발 서버 시작 시 실행
      configureServer() {
        generateFirebaseConfig();
      },
      // 빌드 시작 시 실행
      buildStart() {
        generateFirebaseConfig();
      }
    };
  }

  function generateFirebaseConfig() {
    const config = {
      apiKey: process.env.VITE_FIREBASE_API_KEY || '',
      authDomain: process.env.VITE_FIREBASE_AUTH_DOMAIN || '',
      projectId: process.env.VITE_FIREBASE_PROJECT_ID || '',
      storageBucket: process.env.VITE_FIREBASE_STORAGE_BUCKET || '',
      messagingSenderId: process.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
      appId: process.env.VITE_FIREBASE_APP_ID || '',
      measurementId: process.env.VITE_FIREBASE_MEASUREMENT_ID || ''
    };

    const configContent = `// Auto-generated file - DO NOT EDIT
// Firebase 환경변수 (Service Worker용)
self.FIREBASE_CONFIG = ${JSON.stringify(config, null, 2)};
`;

    const publicDir = path.resolve(__dirname, 'public');
    fs.writeFileSync(path.join(publicDir, 'firebase-config.js'), configContent);
  }

  export default defineConfig({
    plugins: [react(), firebaseConfigPlugin()],
    resolve: {
      extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
      alias: {
        'vaul@1.1.2': 'vaul',
        'sonner@2.0.3': 'sonner',
        'recharts@2.15.2': 'recharts',
        'react-resizable-panels@2.1.7': 'react-resizable-panels',
        'react-hook-form@7.55.0': 'react-hook-form',
        'react-day-picker@8.10.1': 'react-day-picker',
        'next-themes@0.4.6': 'next-themes',
        'lucide-react@0.487.0': 'lucide-react',
        'input-otp@1.4.2': 'input-otp',
        'figma:asset/f84c8d08dbe6e75dfc4c19c8c7ceedae02b45f80.png': path.resolve(__dirname, './src/assets/f84c8d08dbe6e75dfc4c19c8c7ceedae02b45f80.png'),
        'figma:asset/c5afd724d968366ab1d939ebd1fa1377e2d4b2bb.png': path.resolve(__dirname, './src/assets/c5afd724d968366ab1d939ebd1fa1377e2d4b2bb.png'),
        'figma:asset/bae562f0a5cb614be1ddd76987a8beda0506f3a2.png': path.resolve(__dirname, './src/assets/bae562f0a5cb614be1ddd76987a8beda0506f3a2.png'),
        'figma:asset/9e37fa47dd4c36536c3dbff797de36f7c97e61ae.png': path.resolve(__dirname, './src/assets/9e37fa47dd4c36536c3dbff797de36f7c97e61ae.png'),
        'figma:asset/9d8f5cd1f203a3bfab1fdbfc94c0e49b7e5e0cc2.png': path.resolve(__dirname, './src/assets/9d8f5cd1f203a3bfab1fdbfc94c0e49b7e5e0cc2.png'),
        'figma:asset/68d6b45c96227e3194749364b7a79d356611856c.png': path.resolve(__dirname, './src/assets/68d6b45c96227e3194749364b7a79d356611856c.png'),
        'figma:asset/5ed0cbf4b0233625f7aaba0474ed0928adb308e3.png': path.resolve(__dirname, './src/assets/5ed0cbf4b0233625f7aaba0474ed0928adb308e3.png'),
        'figma:asset/5834b4abc3349e218166a4aa12b23ef87542b6a1.png': path.resolve(__dirname, './src/assets/5834b4abc3349e218166a4aa12b23ef87542b6a1.png'),
        'figma:asset/4d414e7f8b2ce1c94b21db7928abbc05f3b6ee0f.png': path.resolve(__dirname, './src/assets/4d414e7f8b2ce1c94b21db7928abbc05f3b6ee0f.png'),
        'figma:asset/0f479ae13aeeec4c4c5fbe69018d93878895c993.png': path.resolve(__dirname, './src/assets/0f479ae13aeeec4c4c5fbe69018d93878895c993.png'),
        'embla-carousel-react@8.6.0': 'embla-carousel-react',
        'cmdk@1.1.1': 'cmdk',
        'class-variance-authority@0.7.1': 'class-variance-authority',
        '@radix-ui/react-tooltip@1.1.8': '@radix-ui/react-tooltip',
        '@radix-ui/react-toggle@1.1.2': '@radix-ui/react-toggle',
        '@radix-ui/react-toggle-group@1.1.2': '@radix-ui/react-toggle-group',
        '@radix-ui/react-tabs@1.1.3': '@radix-ui/react-tabs',
        '@radix-ui/react-switch@1.1.3': '@radix-ui/react-switch',
        '@radix-ui/react-slot@1.1.2': '@radix-ui/react-slot',
        '@radix-ui/react-slider@1.2.3': '@radix-ui/react-slider',
        '@radix-ui/react-separator@1.1.2': '@radix-ui/react-separator',
        '@radix-ui/react-select@2.1.6': '@radix-ui/react-select',
        '@radix-ui/react-scroll-area@1.2.3': '@radix-ui/react-scroll-area',
        '@radix-ui/react-radio-group@1.2.3': '@radix-ui/react-radio-group',
        '@radix-ui/react-progress@1.1.2': '@radix-ui/react-progress',
        '@radix-ui/react-popover@1.1.6': '@radix-ui/react-popover',
        '@radix-ui/react-navigation-menu@1.2.5': '@radix-ui/react-navigation-menu',
        '@radix-ui/react-menubar@1.1.6': '@radix-ui/react-menubar',
        '@radix-ui/react-label@2.1.2': '@radix-ui/react-label',
        '@radix-ui/react-hover-card@1.1.6': '@radix-ui/react-hover-card',
        '@radix-ui/react-dropdown-menu@2.1.6': '@radix-ui/react-dropdown-menu',
        '@radix-ui/react-dialog@1.1.6': '@radix-ui/react-dialog',
        '@radix-ui/react-context-menu@2.2.6': '@radix-ui/react-context-menu',
        '@radix-ui/react-collapsible@1.1.3': '@radix-ui/react-collapsible',
        '@radix-ui/react-checkbox@1.1.4': '@radix-ui/react-checkbox',
        '@radix-ui/react-avatar@1.1.3': '@radix-ui/react-avatar',
        '@radix-ui/react-aspect-ratio@1.1.2': '@radix-ui/react-aspect-ratio',
        '@radix-ui/react-alert-dialog@1.1.6': '@radix-ui/react-alert-dialog',
        '@radix-ui/react-accordion@1.2.3': '@radix-ui/react-accordion',
        '@': path.resolve(__dirname, './src'),
      },
    },
    publicDir: 'public',
    build: {
      target: 'esnext',
      outDir: 'dist',
      rollupOptions: {},
    },
    server: {
      port: 3000,
      open: true,
       proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true }, // 필요하면 유지
    },
  },
});
