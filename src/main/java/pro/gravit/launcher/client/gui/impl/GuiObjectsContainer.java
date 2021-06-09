package pro.gravit.launcher.client.gui.impl;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.overlays.*;
import pro.gravit.launcher.client.gui.scenes.*;
import pro.gravit.launcher.client.gui.scenes.console.ConsoleScene;
import pro.gravit.launcher.client.gui.scenes.debug.DebugScene;
import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.client.gui.scenes.login.methods.WebAuthMethod;
import pro.gravit.launcher.client.gui.scenes.options.OptionsScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.client.gui.scenes.serverinfo.ServerInfoScene;
import pro.gravit.launcher.client.gui.scenes.settings.SettingsScene;
import pro.gravit.launcher.client.gui.scenes.update.UpdateScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;
import pro.gravit.utils.helper.LogHelper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Set;

public class GuiObjectsContainer {
    private final JavaFXApplication application;
    private final Set<AbstractOverlay> overlays = new HashSet<>();
    private final Set<AbstractScene> scenes = new HashSet<>();
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
        loginScene = registerScene(LoginScene.class);
        processingOverlay = registerOverlay(ProcessingOverlay.class);

        serverMenuScene = registerScene(ServerMenuScene.class);
        serverInfoScene = registerScene(ServerInfoScene.class);
        optionsScene = registerScene(OptionsScene.class);
        settingsScene = registerScene(SettingsScene.class);
        consoleScene = registerScene(ConsoleScene.class);

        updateScene = registerScene(UpdateScene.class);
        debugScene = registerScene(DebugScene.class);
    }

    public void reload() throws Exception {
        Class<? extends AbstractScene> scene = application.getCurrentScene().getClass();
        ContextHelper.runInFxThreadStatic(() -> {
            application.getMainStage().stage.setScene(null);
            overlays.clear();
            scenes.clear();
            init();
            for(AbstractScene s : scenes) {
                if(s.getClass() == scene) {
                    application.getMainStage().setScene(s);
                }
            }
        }).get();
    }


    @SuppressWarnings("unchecked")
    public <T extends AbstractOverlay> T registerOverlay(Class<T> clazz) {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, JavaFXApplication.class)).invokeWithArguments(application);
            overlays.add(instance);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractScene> T registerScene(Class<T> clazz) {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, JavaFXApplication.class)).invokeWithArguments(application);
            scenes.add(instance);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }
}
