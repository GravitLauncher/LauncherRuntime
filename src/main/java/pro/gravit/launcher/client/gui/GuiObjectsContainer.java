package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.overlay.*;
import pro.gravit.launcher.client.gui.scene.ConsoleScene;
import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.client.gui.scene.ServerMenuScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;

public class GuiObjectsContainer {
    private final JavaFXApplication application;
    public ProcessingOverlay processingOverlay;
    public UpdateOverlay updateOverlay;
    public DebugOverlay debugOverlay;

    public ServerMenuScene serverMenuScene;
    public LoginScene loginScene;
    public OptionsOverlay optionsOverlay;
    public SettingsOverlay settingsOverlay;
    public ConsoleScene consoleScene;

    public ConsoleStage consoleStage;

    public GuiObjectsContainer(JavaFXApplication application) {
        this.application = application;
    }

    public void init() {
        loginScene = application.registerScene(LoginScene.class);
        processingOverlay = application.registerOverlay(ProcessingOverlay.class);

        serverMenuScene = application.registerScene(ServerMenuScene.class);
        optionsOverlay = application.registerOverlay(OptionsOverlay.class);
        settingsOverlay = application.registerOverlay(SettingsOverlay.class);
        consoleScene = application.registerScene(ConsoleScene.class);

        updateOverlay = application.registerOverlay(UpdateOverlay.class);
        debugOverlay = application.registerOverlay(DebugOverlay.class);
    }
}
