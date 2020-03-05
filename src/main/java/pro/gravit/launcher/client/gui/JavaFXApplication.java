package pro.gravit.launcher.client.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.UserSettings;
import pro.gravit.launcher.client.gui.commands.DialogCommand;
import pro.gravit.launcher.client.gui.commands.NotifyCommand;
import pro.gravit.launcher.client.gui.commands.VersionCommand;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.MessageManager;
import pro.gravit.launcher.client.gui.stage.PrimaryStage;
import pro.gravit.launcher.managers.ConsoleManager;
import pro.gravit.launcher.managers.SettingsManager;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.websockets.StdWebSocketService;
import pro.gravit.utils.command.BaseCommandCategory;
import pro.gravit.utils.command.CommandHandler;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class JavaFXApplication extends Application {
    public NewLauncherSettings settings;
    public RuntimeSettings runtimeSettings;
    public final LauncherConfig config = Launcher.getConfig();
    public StdWebSocketService service;
    public GuiObjectsContainer gui;
    public RuntimeStateMachine runtimeStateMachine;
    public MessageManager messageManager;
    public ResourceBundle resources;
    private SettingsManager settingsManager;
    private FXMLProvider fxmlProvider;

    private PrimaryStage mainStage;

    public AbstractScene getCurrentScene() {
        return mainStage.getScene();
    }

    public PrimaryStage getMainStage() {
        return mainStage;
    }

    public ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public final ExecutorService workers = Executors.newCachedThreadPool();
    private static final AtomicReference<JavaFXApplication> INSTANCE = new AtomicReference<>();

    public static JavaFXApplication getInstance() {
        return INSTANCE.get();
    }

    public JavaFXApplication() {
        INSTANCE.set(this);
    }

    @Override
    public void init() throws Exception {
        settingsManager = new StdSettingsManager();
        UserSettings.providers.register("stdruntime", RuntimeSettings.class);
        settingsManager.loadConfig();
        settings = settingsManager.getConfig();
        if (settings.userSettings.get("stdruntime") == null)
            settings.userSettings.put("stdruntime", RuntimeSettings.getDefault());
        try {
            settingsManager.loadHDirStore();
        } catch (Exception e) {
            LogHelper.error(e);
        }
        runtimeSettings = (RuntimeSettings) settings.userSettings.get("stdruntime");
        runtimeSettings.apply();
        DirBridge.dirUpdates = runtimeSettings.updatesDir == null ? DirBridge.defaultUpdatesDir : runtimeSettings.updatesDir;
        service = Request.service;
        service.registerEventHandler(new GuiEventHandler(this));
        runtimeStateMachine = new RuntimeStateMachine();
        messageManager = new MessageManager(this);
        registerCommands();
    }

    @Override
    public void start(Stage stage) throws Exception {
        // System loading
        if (runtimeSettings.locale == null) runtimeSettings.locale = RuntimeSettings.DEFAULT_LOCALE;
        try (InputStream input = getResource(String.format("runtime_%s.properties", runtimeSettings.locale.name))) {
            resources = new PropertyResourceBundle(input);
        }
        fxmlProvider = new FXMLProvider(this::newFXMLLoader, workers);
        mainStage = new PrimaryStage(stage, config.projectName.concat(" Launcher"));
        //Overlay loading
        gui = new GuiObjectsContainer(this);
        gui.init();
        //
        mainStage.setScene(gui.loginScene);
        //
        AuthRequest.registerProviders();
    }

    private void registerCommands() {
        BaseCommandCategory category = new BaseCommandCategory();
        category.registerCommand("notify", new NotifyCommand(messageManager));
        category.registerCommand("dialog", new DialogCommand(messageManager));
        category.registerCommand("version", new VersionCommand());
        ConsoleManager.handler.registerCategory(new CommandHandler.Category(category, "runtime"));
    }

    @Override
    public void stop() throws Exception {
        settingsManager.saveConfig();
        settingsManager.saveHDirStore();
    }

    public InputStream getResource(String name) throws IOException {
        return IOHelper.newInput(Launcher.getResourceURL(name));
    }

    public FXMLLoader newFXMLLoader(String name) {
        FXMLLoader loader;
        try {
            loader = new FXMLLoader(IOHelper.getResourceURL("runtime/".concat(name)));
            if (resources != null) {
                loader.setResources(resources);
            }
        } catch (Exception e) {
            LogHelper.error(e);
            return null;
        }
        loader.setCharset(IOHelper.UNICODE_CHARSET);
        return loader;
    }

    public <T> Future<T> queueFxml(String name) throws IOException {
        InputStream input = getResource(name);
        return fxmlProvider.queue(name, input);
    }

    public <T> T getFxml(String name) throws IOException, InterruptedException {
        return fxmlProvider.getFxml(name);
    }

    public <T> Future<T> getNoCacheFxml(String name) throws IOException {
        InputStream input = getResource(name);
        return fxmlProvider.queueNoCache(name, input);
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
    public final String getLangResource(String name)
    {
        return resources.getString(name);
    }
    public final String getLangString(String name, Object... args)
    {
        return String.format(resources.getString(name), args);
    }
    public final String getLangString(String key, String defaultValue)
    {
        try {
            return resources.getString(key);
        } catch (Throwable e)
        {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractScene> T registerScene(Class<T> clazz) {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, JavaFXApplication.class)).invokeWithArguments(this);
            queueFxml(instance.fxmlPath);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractOverlay> T registerOverlay(Class<T> clazz) {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, JavaFXApplication.class)).invokeWithArguments(this);
            queueFxml(instance.fxmlPath);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }
}