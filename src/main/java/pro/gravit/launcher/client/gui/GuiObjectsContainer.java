package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.overlay.DebugOverlay;
import pro.gravit.launcher.client.gui.overlay.ProcessingOverlay;
import pro.gravit.launcher.client.gui.overlay.UpdateOverlay;
import pro.gravit.launcher.client.gui.scene.*;
import pro.gravit.utils.helper.LogHelper;

public class GuiObjectsContainer {
    private final JavaFXApplication application;
    public ProcessingOverlay processingOverlay;
    public UpdateOverlay updateOverlay;
    public DebugOverlay debugOverlay;

    public ServerMenuScene serverMenuScene;
    public LoginScene loginScene;
    public OptionsScene optionsScene;
    public SettingsScene settingsScene;
    public final ObjectContainer objects = new ObjectContainer();

    public GuiObjectsContainer(JavaFXApplication application) {
        this.application = application;
    }

    public void init()
    {
        loginScene = application.registerScene(LoginScene.class);
        processingOverlay = application.registerOverlay(ProcessingOverlay.class);
        updateOverlay = application.registerOverlay(UpdateOverlay.class);

        serverMenuScene = application.registerScene(ServerMenuScene.class);
        optionsScene = application.registerScene(OptionsScene.class);
        settingsScene = application.registerScene(SettingsScene.class);
        debugOverlay = application.registerOverlay(DebugOverlay.class);
    }
}
