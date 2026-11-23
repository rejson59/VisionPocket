// Patchuje wygenerowany projekt Android: uprawnienia, orientacja, pełny ekran.
// Uruchamiane w CI po `npx cap add android`.
import { readFileSync, writeFileSync, existsSync } from 'node:fs';

const manifestPath = 'android/app/src/main/AndroidManifest.xml';
if (!existsSync(manifestPath)) {
  console.error(`[patch-android] Nie znaleziono ${manifestPath}`);
  process.exit(1);
}

let xml = readFileSync(manifestPath, 'utf8');

const PERMISSIONS = [
  'android.permission.CAMERA',
  'android.permission.RECORD_AUDIO',
  'android.permission.MODIFY_AUDIO_SETTINGS',
  'android.permission.WAKE_LOCK',
  'android.permission.INTERNET',
  'android.permission.VIBRATE',
];

const missing = PERMISSIONS.filter((p) => !xml.includes(p));
if (missing.length) {
  const lines = missing
    .map((p) => `    <uses-permission android:name="${p}" />`)
    .join('\n');
  xml = xml.replace(/\s*<application/, `\n${lines}\n\n    <application`);
}

if (!xml.includes('android.hardware.camera')) {
  xml = xml.replace(
    /<application/,
    `    <uses-feature android:name="android.hardware.camera" android:required="false" />\n    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />\n\n    <application`,
  );
}

// pełny ekran + pozioma orientacja + brak restartu przy obrocie
const activityMatch = xml.match(/<activity[\s\S]*?>/);
if (activityMatch) {
  let activity = activityMatch[0];
  if (!activity.includes('android:screenOrientation')) {
    activity = activity.replace(
      /<activity/,
      '<activity\n            android:screenOrientation="sensorLandscape"',
    );
  }
  if (!activity.includes('android:resizeableActivity')) {
    activity = activity.replace(
      /<activity/,
      '<activity\n            android:resizeableActivity="false"',
    );
  }
  if (!activity.includes('android:hardwareAccelerated')) {
    activity = activity.replace(
      /<activity/,
      '<activity\n            android:hardwareAccelerated="true"',
    );
  }
  xml = xml.replace(activityMatch[0], activity);
}

// immersive: pełny ekran bez paska statusu (dodatkowo do StatusBar.hide())
if (!xml.includes('android:windowLayoutInDisplayCutoutMode')) {
  xml = xml.replace(
    /<application/,
    '<application\n        android:windowLayoutInDisplayCutoutMode="shortEdges"',
  );
}

writeFileSync(manifestPath, xml);

// --- wersja aplikacji (żeby było widać, że to nowy build) ---
const gradlePath = 'android/app/build.gradle';
const VERSION_NAME = '0.2.0';
const VERSION_CODE = '2';
if (existsSync(gradlePath)) {
  let gradle = readFileSync(gradlePath, 'utf8');
  if (!/versionCode\s+\d+/.test(gradle)) {
    gradle = gradle.replace(/defaultConfig\s*\{/, (m) => `${m}\n        versionCode ${VERSION_CODE}\n        versionName "${VERSION_NAME}"`);
  } else {
    gradle = gradle.replace(/versionCode\s+\d+/, `versionCode ${VERSION_CODE}`);
    if (/versionName/.test(gradle)) {
      gradle = gradle.replace(/versionName\s+"[^"]*"/, `versionName "${VERSION_NAME}"`);
    } else {
      gradle = gradle.replace(/versionCode\s+\d+/, (m) => `${m}\n        versionName "${VERSION_NAME}"`);
    }
  }
  writeFileSync(gradlePath, gradle);
  console.log(`[patch-android] versionName ${VERSION_NAME} (${VERSION_CODE})`);
}
console.log('[patch-android] Manifest zaktualizowany:');
console.log(
  PERMISSIONS.map((p) => `  · ${p}`).join('\n'),
  '\n  · screenOrientation=sensorLandscape\n  · hardwareAccelerated=true',
);
