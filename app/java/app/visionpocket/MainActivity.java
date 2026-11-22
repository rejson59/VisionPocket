package app.visionpocket;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

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
        chimeId = soundPool.load(this, R.raw.chime, 1);
        clickId = soundPool.load(this, R.raw.click, 1);
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

    public void openVideoPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        try {
            startActivityForResult(i, 7);
        } catch (Exception ignored) {}
    }

    public void openImagePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, 8);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        if (requestCode == 7) {
            renderer.onVideoPicked(u);
        } else if (requestCode == 8) {
            renderer.onImagePicked(u);
        }
    }

    public void playChime() {
        try {
            if (soundPool != null) soundPool.play(chimeId, 0.5f, 0.5f, 1, 0, 1f);
        } catch (Exception ignored) {}
    }

    public void playClick() {
        try {
            if (soundPool != null) soundPool.play(clickId, 0.4f, 0.4f, 2, 0, 1f);
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
    }
}
