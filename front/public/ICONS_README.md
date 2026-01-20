# PWA 아이콘 생성 가이드

## 필요한 아이콘 파일 목록

다음 아이콘 파일들을 `front/public/assets/icons/` 폴더에 추가해야 합니다:

### 필수 아이콘
- `icon-72x72.png` (72x72px)
- `icon-96x96.png` (96x96px)
- `icon-128x128.png` (128x128px)
- `icon-144x144.png` (144x144px)
- `icon-152x152.png` (152x152px)
- `icon-192x192.png` (192x192px) - Android Chrome PWA
- `icon-384x384.png` (384x384px)
- `icon-512x512.png` (512x512px) - Android Chrome PWA
- `apple-touch-icon.png` (180x180px) - iOS Safari
- `badge-72x72.png` (72x72px) - 알림 배지 (선택)

### 추가 아이콘 (선택)
- `screenshot-1.png` (540x720px) - PWA 스토어 스크린샷

## 아이콘 생성 방법

### 옵션 1: 온라인 도구 사용 (추천)
1. 512x512px 이상의 원본 이미지 준비
2. 다음 사이트 중 하나 사용:
   - https://realfavicongenerator.net/
   - https://www.pwabuilder.com/imageGenerator
   - https://favicon.io/

### 옵션 2: 로컬 도구 사용
```bash
# ImageMagick 설치 후 사용
convert logo.png -resize 192x192 icon-192x192.png
convert logo.png -resize 512x512 icon-512x512.png
convert logo.png -resize 180x180 apple-touch-icon.png
```

### 옵션 3: 직접 디자인
- Figma, Sketch, Adobe Illustrator 등에서 디자인
- 각 사이즈별로 export

## 디자인 가이드

### 앱 아이콘 (icon-*.png)
- 배경: 현재 테마 색상 `#007DFC` (파란색)
- 로고: 파도 모양 (현재 앱 로고와 동일)
- 여백: 각 면에 10% 정도
- 모서리: 둥글게 처리하지 말 것 (OS가 자동 처리)

### iOS 아이콘 (apple-touch-icon.png)
- 사이즈: 180x180px
- 배경: 단색 또는 그라데이션 권장
- iOS가 자동으로 둥근 모서리 적용

### 알림 배지 (badge-72x72.png)
- 사이즈: 72x72px
- 단색 또는 투명 배경
- 간단한 아이콘 (예: 파도 심볼)

## 임시 아이콘 생성 (개발용)

개발 중에는 placeholder 아이콘을 사용할 수 있습니다:

```bash
# public 폴더에서 실행
# 단색 placeholder 생성 (ImageMagick 필요)
for size in 72 96 128 144 152 192 384 512; do
  convert -size ${size}x${size} xc:#007DFC -fill white -gravity center \
    -pointsize $((size/4)) -annotate +0+0 "PWA" icon-${size}x${size}.png
done

convert -size 180x180 xc:#007DFC -fill white -gravity center \
  -pointsize 45 -annotate +0+0 "PWA" apple-touch-icon.png
```

## 현재 상태

✅ **아이콘 파일이 모두 생성되었습니다!**

다음 파일들이 자동으로 생성되어 있습니다:
- ✅ icon-72x72.png
- ✅ icon-96x96.png
- ✅ icon-128x128.png
- ✅ icon-144x144.png
- ✅ icon-152x152.png
- ✅ icon-192x192.png
- ✅ icon-384x384.png
- ✅ icon-512x512.png
- ✅ apple-touch-icon.png (180x180px)
- ✅ badge-72x72.png

## 아이콘 재생성 방법

아이콘을 수정하거나 재생성하려면:

```bash
# 방법 1: npm 스크립트 사용
npm run generate-icons

# 방법 2: Node.js 직접 실행
node generate-icons.js
```

아이콘 디자인을 변경하려면 `public/assets/icons/logo.svg` 파일을 수정한 후 위 명령어를 실행하세요.

## 파일 구조

```
front/public/
└── assets/
    └── icons/
        ├── logo.svg               # 원본 로고 (512x512)
        ├── icon-72x72.png
        ├── icon-96x96.png
        ├── icon-128x128.png
        ├── icon-144x144.png
        ├── icon-152x152.png
        ├── icon-192x192.png       # PWA 필수
        ├── icon-384x384.png
        ├── icon-512x512.png       # PWA 필수
        ├── apple-touch-icon.png   # iOS 필수 (180x180)
        └── badge-72x72.png        # 알림 배지
```

## 참고 사항

- 아이콘 파일명은 `manifest.webmanifest`와 `index.html`에 정의된 경로와 일치해야 함
- 아이콘은 반드시 PNG 형식이어야 함
- 투명 배경보다는 단색 배경 권장 (iOS 호환성)