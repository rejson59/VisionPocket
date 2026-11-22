package app.visionpocket;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;

import java.util.Random;

/** Classic 2048 in a window, Quest-style neutral tiles, arrow buttons. */
public class Game2048Window implements WindowContent {

    public final VpWindows.Window window;
    private final Panel chrome;
    private final VpWindows wm;
    private final VrRenderer ren;
    private final int[] board = new int[16];
    private int score = 0;
    private boolean over = false;
    private final Random rnd = new Random();

    private static final int[] TILE_COLORS = {
            0x00000000, 0xFF1D1F24, 0xFF2E323A, 0xFF3A3F49, 0xFF4A5060, 0xFF5A6172,
            0xFF6E7688, 0xFF828A9C, 0xFF96A0B0, 0xFFAAB2C0, 0xFFBFC5D0, 0xFFD2D6DE,
            0xFFE8EAEE
    };

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnHover = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMsg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNum = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Game2048Window(VpWindows wm, VrRenderer ren) {
        this.wm = wm;
        this.ren = ren;
        window = new VpWindows.Window("g2048", ren.activity().getString(R.string.win_2048));
        chrome = new Panel(0, 0, 0, 0, 1.2f, 1.34f, 800f);
        window.addPart(chrome, 0, 0, 0);
        window.attachChrome(chrome);
        window.content = this;

        int s = VpWindows.STRIP_PX;
        chrome.add("new", 40, 24 + s, 300, 116 + s);
        chrome.add("exit", 620, 24 + s, 880, 116 + s);
        // arrows
        chrome.add("up", 340, 620 + s, 560, 740 + s);
        chrome.add("left", 110, 760 + s, 330, 880 + s);
        chrome.add("down", 340, 760 + s, 560, 880 + s);
        chrome.add("right", 570, 760 + s, 790, 880 + s);
        chrome.click = b -> onClick(b.id);

        pBg.setColor(0xFF101113);
        pBtn.setColor(0xFF1F2126);
        pBtnHover.setColor(0xFF2E323A);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(3f);
        pBorder.setColor(0xFF2E3138);
        pLabel.setColor(0xFFF2F3F5);
        pLabel.setTextSize(52f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pVal.setColor(0xFFE8ECF5);
        pVal.setTextSize(46f);
        pVal.setTextAlign(Paint.Align.CENTER);
        pMsg.setColor(0xFF9BA0AB);
        pMsg.setTextSize(38f);
        pMsg.setTextAlign(Paint.Align.CENTER);
        pNum.setTextAlign(Paint.Align.CENTER);
        pNum.setFakeBoldText(true);

        reset();
        wm.placeNew(window);
    }

    private void reset() {
        java.util.Arrays.fill(board, 0);
        score = 0;
        over = false;
        addTile();
        addTile();
        chrome.dirty = true;
    }

    private void addTile() {
        int empties = 0;
        for (int v : board) if (v == 0) empties++;
        if (empties == 0) return;
        int n = rnd.nextInt(empties) + 1;
        for (int i = 0; i < 16; i++) {
            if (board[i] == 0 && --n == 0) {
                board[i] = rnd.nextInt(10) < 9 ? 2 : 4;
                return;
            }
        }
    }

    /** 0=up 1=down 2=left 3=right. Returns true if the board changed. */
    private boolean move(int dir) {
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            int[] line = new int[4];
            for (int j = 0; j < 4; j++) {
                int idx = index(i, j, dir);
                line[j] = board[idx];
            }
            int[] merged = collapse(line, true);
            for (int j = 0; j < 4; j++) {
                int idx = index(i, j, dir);
                if (board[idx] != merged[j]) { board[idx] = merged[j]; changed = true; }
            }
        }
        return changed;
    }

    private int index(int i, int j, int dir) {
        switch (dir) {
            case 0: return j * 4 + i;            // up: rows top->bottom
            case 1: return (3 - j) * 4 + i;      // down
            case 2: return i * 4 + j;            // left
            default: return i * 4 + (3 - j);     // right
        }
    }

    private int[] collapse(int[] line, boolean scoring) {
        int[] out = new int[4];
        int pos = 0;
        int prev = 0;
        for (int v : line) {
            if (v == 0) continue;
            if (prev == v) {
                out[pos - 1] = v * 2;
                if (scoring) score += v * 2;
                prev = 0;
            } else {
                out[pos++] = v;
                prev = v;
            }
        }
        return out;
    }

    private boolean canMove() {
        for (int v : board) if (v == 0) return true;
        for (int dir = 0; dir < 4; dir++) {
            for (int i = 0; i < 4; i++) {
                int[] line = new int[4];
                for (int j = 0; j < 4; j++) line[j] = board[index(i, j, dir)];
                int[] merged = collapse(line, false);
                for (int j = 0; j < 4; j++) if (merged[j] != line[j]) return true;
            }
        }
        return false;
    }

    private void doMove(int dir) {
        if (over) return;
        if (move(dir)) {
            addTile();
            if (!canMove()) {
                over = true;
                SettingsStore st = ren.settings();
                if (score > st.best2048()) st.setBest2048(score);
            }
            chrome.dirty = true;
        }
    }

    private void drawBody(Canvas c, Panel p, boolean focused) {
        int s = VpWindows.STRIP_PX;
        pBg.setColor(0xFF101113);
        c.drawRoundRect(0, 0, p.pw, p.ph - s, 0, 0, pBg);

        c.drawText(ren.activity().getString(R.string.game_score) + "  " + score, 210f, 96f, pVal);
        c.drawText(ren.activity().getString(R.string.game_best) + "  " + Math.max(score, ren.settings().best2048()),
                640f, 96f, pVal);

        float cell = 170f, gap = 12f;
        float gx = 68f, gy = 140f;
        for (int i = 0; i < 16; i++) {
            int r = i / 4, col = i % 4;
            float x = gx + col * (cell + gap);
            float y = gy + r * (cell + gap);
            int v = board[i];
            int colorIdx = v == 0 ? 1 : Math.min(TILE_COLORS.length - 1,
                    Integer.numberOfTrailingZeros(v));
            pTile.setColor(TILE_COLORS[colorIdx]);
            c.drawRoundRect(x, y, x + cell, y + cell, 18, 18, pTile);
            if (v > 0) {
                pNum.setColor(colorIdx >= 9 ? 0xFF14161A : 0xFFF2F3F5);
                pNum.setTextSize(v < 100 ? 84f : (v < 1000 ? 72f : 60f));
                c.drawText(String.valueOf(v), x + cell / 2f,
                        y + cell / 2f - (pNum.ascent() + pNum.descent()) / 2f, pNum);
            }
        }

        if (over) {
            c.drawText(ren.activity().getString(R.string.g2048_over), p.pw / 2f, gy + 4 * (cell + gap) + 40f, pMsg);
        }

        for (Panel.Button b : p.buttons) {
            RectF r = new RectF(b.rect.left, b.rect.top - s, b.rect.right, b.rect.bottom - s);
            c.drawRoundRect(r, 20, 20, b.hover ? pBtnHover : pBtn);
            pBorder.setColor(b.hover ? 0xFF8FA3C8 : 0xFF2E3138);
            c.drawRoundRect(r, 20, 20, pBorder);
            String t;
            switch (b.id) {
                case "up": t = "▲"; break;
                case "down": t = "▼"; break;
                case "left": t = "◀"; break;
                case "right": t = "▶"; break;
                case "new": t = ren.activity().getString(R.string.g2048_new); break;
                default: t = ren.activity().getString(R.string.common_close);
            }
            c.drawText(t, r.centerX(), r.centerY() - (pLabel.ascent() + pLabel.descent()) / 2f, pLabel);
        }
    }

    @Override public void draw(Canvas c, Panel p, boolean focused) { drawBody(c, p, focused); }

    @Override public void onClick(String id) {
        switch (id) {
            case "up": doMove(0); break;
            case "down": doMove(1); break;
            case "left": doMove(2); break;
            case "right": doMove(3); break;
            case "new": reset(); break;
            case "exit": wm.close(window); break;
        }
        ren.activity().playClick();
    }

    @Override public void onBodyClick(Panel part) {}

    @Override public void update() {}

    @Override public void onClosed() {
        if (score > ren.settings().best2048()) ren.settings().setBest2048(score);
    }

    @Override public void onRestored() {}
}
