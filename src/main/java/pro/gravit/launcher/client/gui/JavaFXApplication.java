package pro.gravit.launcher.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.*;
import pro.gravit.launcher.api.DialogService;
import pro.gravit.launcher.client.*;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.events.ClientGuiPhase;
import pro.gravit.launcher.client.gui.commands.RuntimeCommand;
import pro.gravit.launcher.client.gui.commands.VersionCommand;
import pro.gravit.launcher.client.gui.config.GuiModuleConfig;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.config.StdSettingsManager;
import pro.gravit.launcher.client.gui.helper.EnFSHelper;
import pro.gravit.launcher.client.gui.impl.*;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.service.*;
import pro.gravit.launcher.client.gui.stage.PrimaryStage;
import pro.gravit.launcher.client.gui.utils.FXMLFactory;
import pro.gravit.launcher.client.gui.utils.TriggerManager;
import pro.gravit.launcher.debug.DebugMain;
import pro.gravit.launcher.managers.ConsoleManager;
import pro.gravit.launcher.managers.SettingsManager;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.RequestService;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.websockets.StdWebSocketService;
import pro.gravit.utils.command.BaseCommandCategory;
import pro.gravit.utils.command.CommandCategory;
import pro.gravit.utils.command.CommandHandler;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class JavaFXApplication extends Application {
    private static final AtomicReference<JavaFXApplication> INSTANCE = new AtomicReference<>();
    private static final AtomicBoolean IS_NOGUI = new AtomicBoolean(false);
    private static Path runtimeDirectory = null;
    public final LauncherConfig config = Launcher.getConfig();
    public final ExecutorService workers = Executors.newWorkStealingPool(4);
    public RuntimeSettings runtimeSettings;
    public RequestService service;
    public GuiObjectsContainer gui;
    public StateService stateService;
    public GuiModuleConfig guiModuleConfig;
    public MessageManager messageManager;
    public RuntimeSecurityService securityService;
    public SkinManager skinManager;
    public FXMLFactory fxmlFactory;
    public JavaService javaService;
    public PingService pingService;
    public OfflineService offlineService;
    public TriggerManager triggerManager;
    private SettingsManager settingsManager;
    private PrimaryStage mainStage;
    private boolean debugMode;
    private ResourceBundle resources;
    private static Path enfsDirectory;

    public JavaFXApplication() {
        INSTANCE.set(this);
    }

    public static JavaFXApplication getInstance() {
        return INSTANCE.get();
    }

    public AbstractScene getCurrentScene() {
        return (AbstractScene) mainStage.getVisualComponent();
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
            settings.userSettings.put(JavaRuntimeModule.RUNTIME_NAME, RuntimeSettings.getDefault(guiModuleConfig));
        try {
            settingsManager.loadHDirStore();
        } catch (Exception e) {
            LogHelper.error(e);
        }
        runtimeSettings = (RuntimeSettings) settings.userSettings.get(JavaRuntimeModule.RUNTIME_NAME);
        runtimeSettings.apply();
        DirBridge.dirUpdates = runtimeSettings.updatesDir == null ? DirBridge.defaultUpdatesDir : runtimeSettings.updatesDir;
        service = Request.getRequestService();
        service.registerEventHandler(new GuiEventHandler(this));
        stateService = new StateService();
        messageManager = new MessageManager(this);
        securityService = new RuntimeSecurityService(this);
        skinManager = new SkinManager(this);
        triggerManager = new TriggerManager(this);
        javaService = new JavaService(this);
        offlineService = new OfflineService(this);
        pingService = new PingService();
        registerCommands();
    }

    @Override
    public void start(Stage stage) throws Exception {
        // If debugging
        try {
            Class.forName("pro.gravit.launcher.debug.DebugMain", false, JavaFXApplication.class.getClassLoader());
            if (DebugMain.IS_DEBUG.get()) {
                runtimeDirectory = IOHelper.WORKING_DIR.resolve("runtime");
                debugMode = true;
            }
        } catch (Throwable e) {
            if (!(e instanceof ClassNotFoundException)) {
                LogHelper.error(e);
            }
        }
        try {
            Class.forName("pro.gravit.utils.enfs.EnFS", false, JavaFXApplication.class.getClassLoader());
            if (runtimeDirectory == null) {
                EnFSHelper.initEnFS();
                enfsDirectory = EnFSHelper.initEnFSDirectory(config, runtimeSettings.theme);
            }
            if (!EnFSHelper.checkEnFSUrl()) {
                JavaRuntimeModule.noEnFSAlert();
            }
        } catch (Throwable e) {
            if (!(e instanceof ClassNotFoundException)) {
                LogHelper.error(e);
            }
        }
        // System loading
        if (runtimeSettings.locale == null)
            runtimeSettings.locale = RuntimeSettings.DEFAULT_LOCALE;
        try {
            updateLocaleResources(runtimeSettings.locale.name);
        } catch (Throwable e) {
            JavaRuntimeModule.noLocaleAlert(runtimeSettings.locale.name);
            if (!(e instanceof FileNotFoundException)) {
                LogHelper.error(e);
            }
            Platform.exit();
        }
        {
            RuntimeDialogService dialogService = new RuntimeDialogService(messageManager);
            DialogService.setDialogImpl(dialogService);
            DialogService.setNotificationImpl(dialogService);
        }
        if(offlineService.isOfflineMode()) {
            if(!offlineService.isAvailableOfflineMode() && !debugMode) {
                messageManager.showDialog(getTranslation("runtime.offline.dialog.header"), getTranslation("runtime.offline.dialog.text"), Platform::exit, Platform::exit, false);
                return;
            }
        }
        try {
            mainStage = new PrimaryStage(stage, String.format("%s Launcher", config.projectName));
            // Overlay loading
            gui = new GuiObjectsContainer(this);
            gui.init();
            //
            if (!IS_NOGUI.get()) {
                mainStage.setScene(gui.loginScene);
                mainStage.show();
                if(offlineService.isOfflineMode()) {
                    messageManager.createNotification(getTranslation("runtime.offline.notification.header"), getTranslation("runtime.offline.notification.text"));
                }
            } else {
                Platform.setImplicitExit(false);
            }
            //
            LauncherEngine.modulesManager.invokeEvent(new ClientGuiPhase(StdJavaRuntimeProvider.getInstance()));
            AuthRequest.registerProviders();
        } catch (Throwable e) {
            LogHelper.error(e);
            JavaRuntimeModule.errorHandleAlert(e);
            Platform.exit();
        }
    }

    public void updateLocaleResources(String locale) throws IOException {
        try (InputStream input = getResource(String.format("runtime_%s.properties", locale))) {
            resources = new PropertyResourceBundle(input);
        }
        fxmlFactory = new FXMLFactory(resources, workers);
    }

    public void resetDirectory() throws IOException {
        if(enfsDirectory != null) {
            enfsDirectory = EnFSHelper.initEnFSDirectory(config, runtimeSettings.theme);
        }
    }

    private CommandCategory runtimeCategory;

    private void registerCommands() {
        runtimeCategory = new BaseCommandCategory();
        runtimeCategory.registerCommand("version", new VersionCommand());
        if (ConsoleManager.isConsoleUnlock) {
            registerPrivateCommands();
        }
        ConsoleManager.handler.registerCategory(new CommandHandler.Category(runtimeCategory, "runtime"));
    }

    public void registerPrivateCommands() {
        if (runtimeCategory == null) return;
        runtimeCategory.registerCommand("runtime", new RuntimeCommand(this));
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
        if (runtimeDirectory != null) {
            Path target = runtimeDirectory.resolve(name);
            if (!Files.exists(target))
                throw new FileNotFoundException(String.format("File runtime/%s not found", name));
            return target.toUri().toURL();
        } else if (enfsDirectory != null) {
            return getResourceEnFs(name);
        } else {
            return Launcher.getResourceURL(name);
        }
    }

    private static URL getResourceEnFs(String name) throws IOException {
        return EnFSHelper.getURL(enfsDirectory.resolve(name).toString().replaceAll("\\\\", "/"));
        //return EnFS.main.getURL(enfsDirectory.resolve(name));
    }

    public URL tryResource(String name) {
        try {
            return getResourceURL(name);
        } catch (IOException e) {
            return null;
        }

    }

    public RuntimeSettings.ProfileSettings getProfileSettings() {
        return getProfileSettings(stateService.getProfile());
    }

    public RuntimeSettings.ProfileSettings getProfileSettings(ClientProfile profile) {
        if (profile == null) throw new NullPointerException("ClientProfile not selected");
        UUID uuid = profile.getUUID();
        RuntimeSettings.ProfileSettings settings = runtimeSettings.profileSettings.get(uuid);
        if (settings == null) {
            settings = RuntimeSettings.ProfileSettings.getDefault(javaService, profile);
            runtimeSettings.profileSettings.put(uuid, settings);
        }
        return settings;
    }

    public static void setNoGUIMode(boolean isNogui) {
        IS_NOGUI.set(isNogui);
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

    public boolean openURL(String url) {
        try {
            getHostServices().showDocument(url);
            return true;
        } catch (Throwable e) {
            LogHelper.error(e);
            return false;
        }
    }

    public void saveSettings() throws IOException {
        settingsManager.saveConfig();
        settingsManager.saveHDirStore();
        if (gui != null && gui.optionsScene != null && stateService != null && stateService.getProfiles() != null) {
            try {
                gui.optionsScene.saveAll();
            } catch (Throwable ex) {
                LogHelper.error(ex);
            }
        }
    }
}