package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Small shared canvas helpers. */
public class HomeSceneUtil {

    static void drawCentered(Canvas c, String text, RectF r, Paint paint) {
        c.drawText(text, r.centerX(), r.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
    }
}
