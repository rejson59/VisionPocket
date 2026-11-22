package app.visionpocket;

import java.util.List;

/** A VR scene: a set of world-locked panels with gaze/tap interaction. */
public interface Scene {
    List<Panel> panels();
    void onShown();
    void onHidden();
    void onClick(Panel.Button b);
    void update();
    void onRecenter(float deltaYaw);
}
