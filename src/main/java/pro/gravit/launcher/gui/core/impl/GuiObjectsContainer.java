package pro.gravit.launcher.gui.core.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.gui.components.FxSceneBackground;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.overlays.ProcessingOverlay;
import pro.gravit.launcher.gui.overlays.UploadAssetOverlay;
import pro.gravit.launcher.gui.overlays.WelcomeOverlay;
import pro.gravit.launcher.gui.scenes.console.ConsoleScene;
import pro.gravit.launcher.gui.scenes.debug.DebugScene;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.launcher.gui.scenes.options.OptionsScene;
import pro.gravit.launcher.gui.scenes.serverinfo.ServerInfoScene;
import pro.gravit.launcher.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.gui.scenes.settings.GlobalSettingsScene;
import pro.gravit.launcher.gui.scenes.settings.SettingsScene;
import pro.gravit.launcher.gui.scenes.update.UpdateScene;
import pro.gravit.launcher.gui.stage.ConsoleStage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class GuiObjectsContainer {

    private static final Logger logger =
            LoggerFactory.getLogger(GuiObjectsContainer.class);

    private final JavaFXApplication application;
    private final Map<String, FxComponent> components = new HashMap<>();
    public ProcessingOverlay processingOverlay;
    public WelcomeOverlay welcomeOverlay;
    public UploadAssetOverlay uploadAssetOverlay;
    public UpdateScene updateScene;
    public DebugScene debugScene;

    public ServerMenuScene serverMenuScene;
    public ServerInfoScene serverInfoScene;
    public LoginScene loginScene;
    public OptionsScene optionsScene;
    public SettingsScene settingsScene;
    public GlobalSettingsScene globalSettingsScene;
    public ConsoleScene consoleScene;

    public ConsoleStage consoleStage;
    public FxSceneBackground background;

    public GuiObjectsContainer(JavaFXApplication application) {
        this.application = application;
    }

    public void init() {
        background = registerComponent(FxSceneBackground::new);
        loginScene = registerComponent(LoginScene::new);
        processingOverlay = registerComponent(ProcessingOverlay::new);
        welcomeOverlay = registerComponent(WelcomeOverlay::new);
        uploadAssetOverlay = registerComponent(UploadAssetOverlay::new);

        serverMenuScene = registerComponent(ServerMenuScene::new);
        serverInfoScene = registerComponent(ServerInfoScene::new);
        optionsScene = registerComponent(OptionsScene::new);
        settingsScene = registerComponent(SettingsScene::new);
        globalSettingsScene = registerComponent(GlobalSettingsScene::new);
        consoleScene = registerComponent(ConsoleScene::new);

        updateScene = registerComponent(UpdateScene::new);
        debugScene = registerComponent(DebugScene::new);
    }

    public Collection<FxComponent> getComponents() {
        return components.values();
    }

    public void reload() throws Exception {
        pro.gravit.launcher.gui.core.ui.FxDispatcher.checkThread();
        if (application.isOperationBusy()) throw new IllegalStateException("Finish the current operation before reloading");
        FxComponent previousScene = application.getCurrentScene();
        String sceneName = previousScene == null ? "login" : previousScene.getName();
        GuiObjectsContainer previous = application.gui;
        var oldResources = application.captureResources();
        GuiObjectsContainer replacement = new GuiObjectsContainer(application);
        try {
            application.resetDirectory();
            application.updateLocaleResources(application.runtimeSettings.locale.name);
            application.gui = replacement;
            replacement.init();
            FxComponent next = replacement.getByName(sceneName);
            if (next == null) throw new IllegalStateException("Unknown route " + sceneName);
            next.currentStage = application.getMainStage();
            next.init();
            replacement.background.init();
            application.getMainStage().setScene(next, false);
            application.getMainStage().resetStyles();
            application.getMainStage().pushBackground(replacement.background);
        } catch (Exception | Error failure) {
            var failedResources = application.captureResources();
            application.gui = previous;
            application.restoreResources(oldResources);
            try {
                application.getMainStage().setScene(previousScene, false);
                application.getMainStage().resetStyles();
                application.getMainStage().pushBackground(previous.background);
            } catch (Exception | Error rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            try { replacement.dispose(); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            if (failedResources.layer() != oldResources.layer()) application.releaseResources(failedResources);
            throw failure;
        }
        try { previous.dispose(); }
        catch (RuntimeException error) { logger.warn("Old UI cleanup failed", error); }
        application.releaseResources(oldResources);
    }

    public void dispose() {
        if (consoleStage != null) consoleStage.close();
        var cleanup = new pro.gravit.launcher.gui.core.ui.UiScope();
        for (FxComponent component : java.util.List.copyOf(components.values())) cleanup.add(component::dispose);
        components.clear();
        cleanup.close();
    }

    public FxComponent getByName(String name) {
        return components.get(name);
    }

    public <T extends FxComponent> T registerComponent(Function<JavaFXApplication, T> constructor) {
        try {
            T instance = constructor.apply(application);
            components.put(instance.getName(), instance);
            return instance;
        } catch (Throwable e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }
    }
}
