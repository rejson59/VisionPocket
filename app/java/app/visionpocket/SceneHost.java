package app.visionpocket;

/** Host services the scenes may use (implemented by VrRenderer). */
public interface SceneHost {
    SettingsStore settings();
    MainActivity activity();
    void recenterView();
    void applyResolution();
    void showScene(SceneManager.SceneId s);
    void requestVideoPicker();
}
