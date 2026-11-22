package app.visionpocket;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Vibrator;
import android.os.VibrationEffect;

public class MainActivity extends Activity {

    private GLSurfaceView glView;
    private VrRenderer renderer;
    private View onboarding;
    private SoundPool soundPool;
    private int chimeId = 0;
    private int clickId = 0;
    private final Handler ui = new Handler();
    private int batteryPct = 100;
    private boolean vrStarted = false;
    private boolean pickForPano = false;
    private SettingsStore settingsStore;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        settingsStore = new SettingsStore(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        // crash-guard: remember the crash so the next launch can tell the user
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { settingsStore.setLastCrash(System.currentTimeMillis()); } catch (Exception ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });

        onboarding = findViewById(R.id.onboarding);
        glView = findViewById(R.id.gl_view);
        renderer = new VrRenderer(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setPreserveEGLContextOnPause(true);
        glView.setOnTouchListener((v, ev) -> {
            if (!vrStarted) return true;
            int type;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: type = 0; break;
                case MotionEvent.ACTION_MOVE: type = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: type = 4; break;
                case MotionEvent.ACTION_POINTER_UP: type = 5; break;
                case MotionEvent.ACTION_UP: type = 2; break;
                case MotionEvent.ACTION_CANCEL: type = 3; break;
                default: return true;
            }
            int n = ev.getPointerCount();
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = ev.getX(i);
                ys[i] = ev.getY(i);
            }
            renderer.postTouch(type, xs, ys);
            return true;
        });

        findViewById(R.id.btn_start).setOnClickListener(v ->
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 11));
        findViewById(R.id.btn_no_camera).setOnClickListener(v -> startVr(false));

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        try {
            chimeId = soundPool.load(this, R.raw.chime, 1);
            clickId = soundPool.load(this, R.raw.click, 1);
        } catch (Exception e) {
            // sounds are optional — never let them kill the launch
        }
    }

    private void startVr(boolean withCamera) {
        vrStarted = true;
        onboarding.setVisibility(View.GONE);
        applyImmersive();
        renderer.setCameraAllowed(withCamera);
        renderer.onVrStarted();
        playChime();
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        getWindow().setAttributes(lp);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 11) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            startVr(granted);
        }
        // POST_NOTIFICATIONS result (requestCode 12) does not block mirroring
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
        renderer.headStart();
        renderer.onAppResume();
        applyImmersive();
        pollBattery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        renderer.headStop();
        renderer.cameraStop();
        glView.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (vrStarted && renderer.back()) return true;
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ================= gamepad (Bluetooth controllers) =================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (vrStarted) {
            int src = event.getSource();
            boolean gamepad = (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                    || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
            if (gamepad) {
                int code = event.getKeyCode();
                if (code == KeyEvent.KEYCODE_BUTTON_A
                        || code == KeyEvent.KEYCODE_BUTTON_B
                        || code == KeyEvent.KEYCODE_BUTTON_R1
                        || code == KeyEvent.KEYCODE_BUTTON_START) {
                    if (event.getRepeatCount() == 0) {
                        renderer.postGamepadButton(code, event.getAction() == KeyEvent.ACTION_DOWN);
                    }
                    return true;
                }
                if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        renderer.postVolume(code == KeyEvent.KEYCODE_VOLUME_UP);
                    }
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (vrStarted) {
            int src = event.getSource();
            if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                float x = event.getAxisValue(MotionEvent.AXIS_X);
                float y = event.getAxisValue(MotionEvent.AXIS_Y);
                float rt = Math.max(
                        Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                                event.getAxisValue(MotionEvent.AXIS_RZ)),
                        event.getAxisValue(MotionEvent.AXIS_GAS));
                renderer.postGamepadStick(x, y);
                renderer.postGamepadTrigger(rt);
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    public void systemVolume(boolean up) {
        try {
            android.media.AudioManager am =
                    (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                    up ? android.media.AudioManager.ADJUST_RAISE
                       : android.media.AudioManager.ADJUST_LOWER,
                    android.media.AudioManager.FLAG_SHOW_UI);
        } catch (Exception ignored) {}
    }

    /** Runtime mic permission for the music visualizer (fallback works without it). */
    public void ensureRecordAudio() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 13);
        }
    }

    // ================= pickers =================

    public void pickVideo(boolean forPano) {
        pickForPano = forPano;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        try {
            startActivityForResult(i, 7);
        } catch (Exception ignored) {}
    }

    public void pickImage(boolean forPano) {
        pickForPano = forPano;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, 8);
        } catch (Exception ignored) {}
    }

    // ================= settings backup =================

    public void exportSettings() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "visionpocket-backup.json");
            startActivityForResult(i, 20);
        } catch (Exception ignored) {}
    }

    public void importSettings() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, 21);
        } catch (Exception ignored) {}
    }

    public void pickSrt() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, 22);
        } catch (Exception ignored) {}
    }

    // ================= screen mirroring =================

    public void startMirrorFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        }
        try {
            MediaProjectionManager mgr =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mgr.createScreenCaptureIntent(), 9);
        } catch (Exception ignored) {}
    }

    public void startMirrorService(Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ================= results =================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == 9) {
            renderer.onProjectionResult(resultCode, data);
            return;
        }
        if (requestCode == 20) {
            try {
                Uri u = data.getData();
                java.io.OutputStream os = getContentResolver().openOutputStream(u);
                os.write(settingsStore.exportJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.close();
                toast(getString(R.string.bk_saved));
            } catch (Exception e) {
                toast(getString(R.string.bk_failed));
            }
            return;
        }
        if (requestCode == 21) {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                int cnt = settingsStore.importJson(bo.toString("UTF-8"));
                if (cnt >= 0) {
                    toast(getString(R.string.bk_restored, cnt));
                    if (glView != null) glView.queueEvent(() -> renderer.applyResolution());
                } else {
                    toast(getString(R.string.bk_failed));
                }
            } catch (Exception e) {
                toast(getString(R.string.bk_failed));
            }
            return;
        }
        Uri u = data.getData();
        if (u == null) return;
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        if (requestCode == 7) {
            if (pickForPano) renderer.onPanoVideoPicked(u);
            else renderer.onVideoPicked(u);
        } else if (requestCode == 8) {
            if (pickForPano) renderer.onPanoImagePicked(u);
            else renderer.onImagePicked(u);
        }
        pickForPano = false;
    }

    // ================= sounds / misc =================

    private void toast(String m) {
        try { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
    }

    public void playChime() {
        try {
            if (soundPool != null) soundPool.play(chimeId, 0.5f, 0.5f, 1, 0, 1f);
        } catch (Exception ignored) {}
    }

    public void playClick() {
        try {
            if (soundPool != null) soundPool.play(clickId, 0.4f, 0.4f, 2, 0, 1f);
            if (settingsStore.haptics() && vibrator != null) {
                vibrator.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}
    }

    public int batteryPct() { return batteryPct; }

    private void pollBattery() {
        ui.postDelayed(() -> {
            try {
                BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                int v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (v > 0) batteryPct = v;
            } catch (Exception ignored) {}
            pollBattery();
        }, 30000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { soundPool.release(); } catch (Exception ignored) {}
        MirrorService.requestStop(this);
    }
}
