/**
 * PWA 아이콘 자동 생성 스크립트
 *
 * 사용법:
 *   npm install sharp --save-dev
 *   node generate-icons.js
 */

const fs = require('fs');
const path = require('path');

// sharp 라이브러리 동적 import
let sharp;
try {
  sharp = require('sharp');
} catch (error) {
  console.error('❌ sharp 라이브러리가 설치되지 않았습니다.');
  console.error('다음 명령어를 실행하세요: npm install sharp --save-dev');
  process.exit(1);
}

const sizes = [
  { name: 'icon-72x72.png', size: 72 },
  { name: 'icon-96x96.png', size: 96 },
  { name: 'icon-128x128.png', size: 128 },
  { name: 'icon-144x144.png', size: 144 },
  { name: 'icon-152x152.png', size: 152 },
  { name: 'icon-192x192.png', size: 192 },
  { name: 'icon-384x384.png', size: 384 },
  { name: 'icon-512x512.png', size: 512 },
  { name: 'apple-touch-icon.png', size: 180 },
  { name: 'badge-72x72.png', size: 72 },
];

const inputSvg = path.join(__dirname, 'public', 'assets', 'icons', 'logo.svg');
const outputDir = path.join(__dirname, 'public', 'assets', 'icons');

async function generateIcons() {
  console.log('🎨 PWA 아이콘 생성 시작...\n');

  if (!fs.existsSync(inputSvg)) {
    console.error(`❌ SVG 파일을 찾을 수 없습니다: ${inputSvg}`);
    process.exit(1);
  }

  for (const { name, size } of sizes) {
    const outputPath = path.join(outputDir, name);

    try {
      await sharp(inputSvg)
        .resize(size, size, {
          fit: 'contain',
          background: { r: 0, g: 125, b: 252, alpha: 1 } // #007DFC
        })
        .png()
        .toFile(outputPath);

      console.log(`✅ ${name} (${size}x${size})`);
    } catch (error) {
      console.error(`❌ ${name} 생성 실패:`, error.message);
    }
  }

  console.log('\n🎉 모든 아이콘 생성 완료!');
  console.log('\n생성된 파일 목록:');
  sizes.forEach(({ name }) => {
    console.log(`  - public/${name}`);
  });
}

generateIcons().catch(console.error);