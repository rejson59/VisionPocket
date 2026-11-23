package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Small UI kit for the neutral, Quest-like design language.
 * One place for colors, buttons, pills and section headers so every window
 * draws consistent chrome. All canvases are the window panels (px).
 */
public final class UiKit {
    private UiKit() {}

    // palette
    public static final int BG = 0xFF101113;
    public static final int CARD = 0xFF1A1C21;
    public static final int CARD2 = 0xFF1F2126;
    public static final int CARD_HOVER = 0xFF2E323A;
    public static final int ACTIVE = 0xFF343945;
    public static final int BORDER = 0xFF2E3138;
    public static final int BORDER_HOVER = 0xFF8FA3C8;
    public static final int TEXT = 0xFFF2F3F5;
    public static final int TEXT_DIM = 0xFF9BA0AB;
    public static final int ACCENT = 0xFF8FA3C8;
    public static final int ACCENT_HOVER = 0xFFB4C2DD;
    public static final int ON_ACCENT = 0xFF101216;
    public static final int OK = 0xFF9BC29B;
    public static final int WARN = 0xFFC8B48F;

    private static Paint pFill, pBorderStroke, pLabel, pLabelDim, pHead, pMono;

    private static Paint fill() {
        if (pFill == null) pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        return pFill;
    }

    private static Paint stroke() {
        if (pBorderStroke == null) {
            pBorderStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            pBorderStroke.setStyle(Paint.Style.STROKE);
            pBorderStroke.setStrokeWidth(3f);
        }
        return pBorderStroke;
    }

    /** Standard button: rounded rect + hairline border + centered label. */
    public static void button(Canvas c, RectF r, String label, boolean hover, boolean primary) {
        Paint f = fill();
        if (primary) f.setColor(hover ? ACCENT_HOVER : ACCENT);
        else f.setColor(hover ? CARD_HOVER : CARD2);
        c.drawRoundRect(r, 22, 22, f);
        Paint b = stroke();
        b.setColor(hover ? BORDER_HOVER : (primary ? BORDER_HOVER : BORDER));
        c.drawRoundRect(r, 22, 22, b);
        if (label != null && !label.isEmpty()) {
            Paint l = labelPaint();
            l.setColor(primary ? ON_ACCENT : TEXT);
            c.drawText(label, r.centerX(), r.centerY() - (l.ascent() + l.descent()) / 2f, l);
        }
    }

    /** Active-state (toggled) chip. */
    public static void chip(Canvas c, RectF r, String label, boolean on, boolean hover) {
        Paint f = fill();
        f.setColor(hover ? CARD_HOVER : (on ? ACTIVE : CARD2));
        c.drawRoundRect(r, 18, 18, f);
        Paint b = stroke();
        b.setColor(on || hover ? BORDER_HOVER : BORDER);
        c.drawRoundRect(r, 18, 18, b);
        Paint l = labelPaint();
        l.setColor(on ? TEXT : TEXT_DIM);
        c.drawText(label, r.centerX(), r.centerY() - (l.ascent() + l.descent()) / 2f, l);
    }

    /** Rounded card surface. */
    public static void card(Canvas c, RectF r) {
        Paint f = fill();
        f.setColor(CARD);
        c.drawRoundRect(r, 20, 20, f);
        Paint b = stroke();
        b.setColor(BORDER);
        c.drawRoundRect(r, 20, 20, b);
    }

    /** Section header (small caps feel). */
    public static void section(Canvas c, String text, float x, float y) {
        Paint h = headPaint();
        c.drawText(text, x, y, h);
        Paint f = fill();
        f.setColor(BORDER);
        c.drawRoundRect(x, y + 14f, x + 90f, y + 17f, 2, 2, f);
    }

    /** Dim status line. */
    public static void status(Canvas c, String text, float x, float y) {
        Paint p = monoPaint();
        c.drawText(text, x, y, p);
    }

    public static Paint labelPaint() {
        if (pLabel == null) {
            pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
            pLabel.setColor(TEXT);
            pLabel.setTextSize(46f);
            pLabel.setTextAlign(Paint.Align.CENTER);
        }
        return pLabel;
    }

    public static Paint headPaint() {
        if (pHead == null) {
            pHead = new Paint(Paint.ANTI_ALIAS_FLAG);
            pHead.setColor(ACCENT);
            pHead.setTextSize(36f);
            pHead.setLetterSpacing(0.12f);
            pHead.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            pHead.setTextAlign(Paint.Align.LEFT);
        }
        return pHead;
    }

    public static Paint monoPaint() {
        if (pMono == null) {
            pMono = new Paint(Paint.ANTI_ALIAS_FLAG);
            pMono.setColor(TEXT_DIM);
            pMono.setTextSize(30f);
            pMono.setTypeface(Typeface.MONOSPACE);
            pMono.setTextAlign(Paint.Align.LEFT);
        }
        return pMono;
    }
}
