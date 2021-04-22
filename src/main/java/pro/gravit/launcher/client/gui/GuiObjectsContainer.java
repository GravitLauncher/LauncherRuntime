package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.overlay.*;
import pro.gravit.launcher.client.gui.scene.*;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;

public class GuiObjectsContainer {
    private final JavaFXApplication application;
    public ProcessingOverlay processingOverlay;
    public UpdateScene updateScene;
    public DebugScene debugScene;

    public ServerMenuScene serverMenuScene;
    public ServerInfoScene serverInfoScene;
    public LoginScene loginScene;
    public OptionsScene optionsScene;
    public SettingsScene settingsScene;
    public ConsoleScene consoleScene;

    public ConsoleStage consoleStage;

    public GuiObjectsContainer(JavaFXApplication application) {
        this.application = application;
    }

    public void init() {
        loginScene = application.registerScene(LoginScene.class);
        processingOverlay = application.registerOverlay(ProcessingOverlay.class);

        serverMenuScene = application.registerScene(ServerMenuScene.class);
        serverInfoScene = application.registerScene(ServerInfoScene.class);
        optionsScene = application.registerScene(OptionsScene.class);
        settingsScene = application.registerScene(SettingsScene.class);
        consoleScene = application.registerScene(ConsoleScene.class);

        updateScene = application.registerScene(UpdateScene.class);
        debugScene = application.registerScene(DebugScene.class);
    }
}
