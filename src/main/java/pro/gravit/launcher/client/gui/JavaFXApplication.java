package pro.gravit.launcher.client.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherAPI;
import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.client.gui.overlay.ProcessingOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.MessageManager;
import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.client.gui.scene.ServerMenuScene;
import pro.gravit.launcher.managers.SettingsManager;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.websockets.StandartClientWebSocketService;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class JavaFXApplication extends Application {
    public NewLauncherSettings settings;
    public StandartClientWebSocketService service;
    public AsyncRequestHandler requestHandler;
    public GuiObjectsContainer gui;
    public RuntimeStateMachine runtimeStateMachine;
    public MessageManager messageManager;
    private SettingsManager settingsManager;
    private FXMLProvider fxmlProvider;

    public Stage getMainStage() {
        return mainStage;
    }

    private Stage mainStage;
    private AbstractScene currentScene;

    public AbstractScene getCurrentScene() {
        return currentScene;
    }

    public ScheduledExecutorService executors = Executors.newScheduledThreadPool(2);
    private static final AtomicReference<JavaFXApplication> INSTANCE = new AtomicReference<>();

    @LauncherAPI
    public static JavaFXApplication getInstance() {
        return INSTANCE.get();
    }

    public JavaFXApplication() {
        INSTANCE.set(this);
    }
    @Override
    public void init() throws Exception {
        settingsManager = new StdSettingsManager();
        settingsManager.loadConfig();
        settings = settingsManager.getConfig();
        try {
            settingsManager.loadHDirStore();
        } catch (Exception e)
        {
            LogHelper.error(e);
        }
        service = Request.service;
        requestHandler = new AsyncRequestHandler(service);
        service.registerHandler(requestHandler);
        gui = new GuiObjectsContainer();
        runtimeStateMachine = new RuntimeStateMachine();
        messageManager = new MessageManager(this);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // System loading
        fxmlProvider = new FXMLProvider(JavaFXApplication::newFXMLLoader, executors);
        //Overlay loading
        gui = new GuiObjectsContainer();
        gui.processingOverlay = new ProcessingOverlay(this);
        //
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        mainStage = stage;

        gui.loginScene = registerScene(LoginScene.class);
        gui.serverMenuScene = registerScene(ServerMenuScene.class);
        messageManager.createNotification("Test head", "Test message", true);
        gui.loginScene.init();
        setMainScene(gui.loginScene);
    }

    @Override
    public void stop() throws Exception {
        settingsManager.saveConfig();
        settingsManager.saveHDirStore();
    }
    public InputStream getResource(String name) throws IOException
    {
        return IOHelper.newInput(Launcher.getResourceURL(name));
    }
    public static FXMLLoader newFXMLLoader(String name)
    {
        FXMLLoader loader;
        try {
            loader = new FXMLLoader(IOHelper.getResourceURL("runtime/".concat(name)));
            loader.setControllerFactory((c) -> {
                LogHelper.debug("ControllerFactory %s", c.getName());
                return null;
            });
        } catch (Exception e) {
            LogHelper.error(e);
            return null;
        }
        loader.setCharset(IOHelper.UNICODE_CHARSET);
        return loader;
    }
    public<T> Future<T> queueFxml(String name) throws IOException
    {
        InputStream input = getResource(name);
        return fxmlProvider.queue(name, input);
    }
    public<T> T getFxml(String name) throws IOException, InterruptedException {
        return fxmlProvider.getFxml(name);
    }
    public<T> Future<T> getNoCacheFxml(String name) throws IOException {
        InputStream input = getResource(name);
        return fxmlProvider.queueNoCache(name, input);
    }
    private void setScene(Scene scene)
    {
        mainStage.setScene(scene);
        mainStage.sizeToScene();
        mainStage.show();
    }
    public void setMainScene(AbstractScene scene) throws Exception
    {
        if(scene == null)
        {
            throw new NullPointerException("Try set null scene");
        }
        if(scene.getScene() == null)
            scene.init();
        currentScene = scene;
        setScene(currentScene.getScene());
    }
    public Stage newStage()
    {
        return newStage(StageStyle.TRANSPARENT);
    }
    public Stage newStage(StageStyle style)
    {
        Stage ret = new Stage();
        ret.initStyle(style);
        ret.setResizable(false);
        return ret;
    }
    @SuppressWarnings("unchecked")
    public<T extends AbstractScene> T registerScene(Class<T> clazz)
    {
        try {
            T instance = (T) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class, Stage.class, JavaFXApplication.class)).invoke(mainStage, this);
            queueFxml(instance.name);
            return instance;
        } catch (Throwable e) {
            LogHelper.error(e);
            throw new RuntimeException(e);
        }
    }
}