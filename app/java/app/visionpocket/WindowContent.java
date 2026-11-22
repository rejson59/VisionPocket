package app.visionpocket;

import android.graphics.Canvas;

/** Content drawn inside a window's body area (below the title strip). */
public interface WindowContent {
    /** Body area is 0..p.pw x 0..(p.ph - VpWindows.STRIP_PX). */
    void draw(Canvas c, Panel p, boolean focused);
    /** A content button was clicked. */
    void onClick(String id);
    /** The window body (not a button, not the video part) was tapped. */
    void onBodyClick(Panel part);
    /** Called every frame while the window exists. */
    void update();
    /** Window is closing: release resources. */
    void onClosed();
    /** Window was restored from minimized state. */
    void onRestored();
}
