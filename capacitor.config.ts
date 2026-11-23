import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'pl.visionpocket.app',
  appName: 'VisionPocket',
  webDir: 'dist',
  backgroundColor: '#05070f',
  android: {
    allowMixedContent: false,
    webContentsDebuggingEnabled: true,
    backgroundColor: '#05070f',
  },
  server: {
    androidScheme: 'https',
  },
};

export default config;
