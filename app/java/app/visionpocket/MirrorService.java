package app.visionpocket;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Foreground service that captures the phone screen (MediaProjection) into a
 * Surface owned by the GL renderer, so the phone display shows up in VR.
 */
public class MirrorService extends Service {

    public static volatile Surface surface;
    public interface StopListener { void onMirrorStopped(); }
    public static volatile StopListener stopListener;

    private static volatile MirrorService instance;

    private MediaProjection projection;
    private VirtualDisplay display;
    private boolean stopping = false;

    public static void requestStop(Context ctx) {
        MirrorService s = instance;
        if (s != null) s.shutdown();
        else ctx.stopService(new Intent(ctx, MirrorService.class));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || stopping) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNow();
        int resultCode = intent.getIntExtra("code", -1);
        Intent data = intent.getParcelableExtra("data");
        try {
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(resultCode, data);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() { shutdown(); }
        }, null);
        Surface s = surface;
        if (s != null) createDisplay(s);
        return START_NOT_STICKY;
    }

    private void startForegroundNow() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("vp", "VisionPocket",
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "vp")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.mirror_notif))
                .setSmallIcon(android.R.drawable.ic_menu_more)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, n);
        }
    }

    private void createDisplay(Surface s) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Display d = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        DisplayMetrics rm = new DisplayMetrics();
        try {
            d.getRealMetrics(rm);
        } catch (Exception e) {
            rm.widthPixels = dm.widthPixels;
            rm.heightPixels = dm.heightPixels;
        }
        try {
            display = projection.createVirtualDisplay("vpmirror",
                    Math.max(2, rm.widthPixels), Math.max(2, rm.heightPixels), dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    s, null, null);
        } catch (Exception ignored) {}
    }

    private void shutdown() {
        if (stopping) return;
        stopping = true;
        try {
            if (display != null) { display.release(); display = null; }
        } catch (Exception ignored) {}
        try {
            if (projection != null) { projection.stop(); projection = null; }
        } catch (Exception ignored) {}
        StopListener l = stopListener;
        if (l != null) l.onMirrorStopped();
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (!stopping) {
            stopping = true;
            try {
                if (display != null) display.release();
                if (projection != null) projection.stop();
            } catch (Exception ignored) {}
            StopListener l = stopListener;
            if (l != null) l.onMirrorStopped();
        }
        super.onDestroy();
    }
}
