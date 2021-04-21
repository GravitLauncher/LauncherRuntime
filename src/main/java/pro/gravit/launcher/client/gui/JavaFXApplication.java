package pro.gravit.launcher.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.client.*;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.events.ClientGuiPhase;
import pro.gravit.launcher.client.gui.commands.DialogCommand;
import pro.gravit.launcher.client.gui.commands.NotifyCommand;
import pro.gravit.launcher.client.gui.commands.VersionCommand;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.MessageManager;
import pro.gravit.launcher.client.gui.stage.PrimaryStage;
import pro.gravit.launcher.debug.DebugMain;
import pro.gravit.launcher.managers.ConsoleManager;
import pro.gravit.launcher.managers.SettingsManager;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.websockets.StdWebSocketService;
import pro.gravit.utils.command.BaseCommandCategory;
import pro.gravit.utils.command.CommandHandler;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class JavaFXApplication extends Application {
    private static final AtomicReference<JavaFXApplication> INSTANCE = new AtomicReference<>();
    private static Path runtimeDirectory = null;
    public final LauncherConfig config = Launcher.getConfig();
    public final ExecutorService workers = Executors.newWorkStealingPool(4);
    public RuntimeSettings runtimeSettings;
    public StdWebSocketService service;
    public GuiObjectsContainer gui;
    public RuntimeStateMachine runtimeStateMachine;
    public GuiModuleConfig guiModuleConfig;
    public MessageManager messageManager;
    public RuntimeSecurityService securityService;
    public SkinManager skinManager;
    public FXMLFactory fxmlFactory;
    private SettingsManager settingsManager;
    private PrimaryStage mainStage;
    private boolean debugMode;
    private ResourceBundle resources;

    public JavaFXApplication() {
        INSTANCE.set(this);
    }

    public static JavaFXApplication getInstance() {
        return INSTANCE.get();
    }

    public AbstractScene getCurrentScene() {
        return mainStage.getScene();
    }

    public PrimaryStage getMainStage() {
        return mainStage;
    }

    @Override
    public void init() throws Exception {
        guiModuleConfig = new GuiModuleConfig();
        settingsManager = new StdSettingsManager();
        UserSettings.providers.register(JavaRuntimeModule.RUNTIME_NAME, RuntimeSettings.class);
        settingsManager.loadConfig();
        NewLauncherSettings settings = settingsManager.getConfig();
        if (settings.userSettings.get(JavaRuntimeModule.RUNTIME_NAME) == null)
            settings.userSettings.put(JavaRuntimeModule.RUNTIME_NAME, RuntimeSettings.getDefault());
        try {
            settingsManager.loadHDirStore();
        } catch (Exception e) {
            LogHelper.error(e);
        }
        runtimeSettings = (RuntimeSettings) settings.userSettings.get(JavaRuntimeModule.RUNTIME_NAME);
        runtimeSettings.apply();
        DirBridge.dirUpdates = runtimeSettings.updatesDir == null ? DirBridge.defaultUpdatesDir : runtimeSettings.updatesDir;
        service = Request.service;
        service.registerEventHandler(new GuiEventHandler(this));
        runtimeStateMachine = new RuntimeStateMachine();
        messageManager = new MessageManager(this);
        securityService = new RuntimeSecurityService(this);
        skinManager = new SkinManager(this);
        registerCommands();
    }

    @Override
    public void start(Stage stage) throws Exception {
        // If debugging
        try {
            Class.forName("pro.gravit.launcher.debug.DebugMain", false, JavaFXApplication.class.getClassLoader());
            if(DebugMain.IS_DEBUG.get()) {
                runtimeDirectory = IOHelper.WORKING_DIR.resolve("runtime");
                debugMode = true;
            }
        } catch (Throwable e) {
            if(!(e instanceof ClassNotFoundException)) {
                LogHelper.error(e);
            }
        }
        // System loading
        if (runtimeSettings.locale == null)
            runtimeSettings.locale = RuntimeSettings.DEFAULT_LOCALE;
        try (InputStream input = getResource(String.format("runtime_%s.properties", runtimeSettings.locale.name))) {
            resources = new PropertyResourceBundle(input);
        } catch (FileNotFoundException e)
        {
            JavaRuntimeModule.noLocaleAlert(runtimeSettings.locale.name);
            Platform.exit();
        }
        try {
            fxmlFactory = new FXMLFactory(resources, workers);
            mainStage = new PrimaryStage(stage, String.format("%s Launcher", config.projectName));
            // Overlay loading
            gui = new GuiObjectsContainer(this);
            gui.init();
            //
            mainStage.setScene(gui.loginScene);
            //
            LauncherEngine.modulesManager.invokeEvent(new ClientGuiPhase(StdJavaRuntimeProvider.getInstance()));
            AuthRequest.registerProviders();
        } catch (Throwable e)
        {
            LogHelper.error(e);
            JavaRuntimeModule.errorHandleAlert(e);
            Platform.exit();
        }
    }

    private void registerCommands() {
        BaseCommandCategory category = new BaseCommandCategory();
        category.registerCommand("notify", new NotifyCommand(messageManager));
        category.registerCommand("dialog", new DialogCommand(messageManager));
        category.registerCommand("version", new VersionCommand());
        ConsoleManager.handler.registerCategory(new CommandHandler.Category(category, "runtime"));
    }



    @Override
    public void stop() {
        LogHelper.debug("JavaFX method stop invoked");
        LauncherEngine.modulesManager.invokeEvent(new ClientExitPhase(0));
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private InputStream getResource(String name) throws IOException {
        return IOHelper.newInput(getResourceURL(name));
    }

    public static URL getResourceURL(String name) throws IOException {
        if(runtimeDirectory == null) {
            return Launcher.getResourceURL(name);
        } else {
            Path target = runtimeDirectory.resolve(name);
            if(!Files.exists(target))
                throw new FileNotFoundException();
            return target.toUri().toURL();
        }
    }

    public URL tryResource(String name) {
        try {
            return getResourceURL(name);
        } catch (IOException e) {
            return null;
        }

    }

    public void setMainScene(AbstractScene scene) throws Exception {
        mainStage.setScene(scene);
    }

    public Stage newStage() {
        return newStage(StageStyle.TRANSPARENT);
    }

    public Stage newStage(StageStyle style) {
        Stage ret = new Stage();
        ret.initStyle(style);
        ret.setResizable(false);
        return ret;
    }

    public final String getTranslation(String name) {
        return getTranslation(name, String.format("'%s'", name));
    }

    public final String getTranslation(String key, String defaultValue) {
        try {
            return resources.getString(key);
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractScene> T registerScene(Class<T> clazz) {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, JavaFXApplication.class)).invokeWithArguments(this);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }

    public boolean openURL(String url) {
        try {
            getHostServices().showDocument(url);
            return true;
        } catch (Throwable e) {
            LogHelper.error(e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractOverlay> T registerOverlay(Class<T> clazz) {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, JavaFXApplication.class)).invokeWithArguments(this);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }

    public void saveSettings() throws IOException {
        settingsManager.saveConfig();
        settingsManager.saveHDirStore();
        if (gui != null && gui.optionsScene != null && runtimeStateMachine != null && runtimeStateMachine.getProfiles() != null) {
            try {
                gui.optionsScene.saveAll();
            } catch (Throwable ex) {
                LogHelper.error(ex);
            }
        }
    }
}