package pro.gravit.launcher.client.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherAPI;
import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.client.gui.overlay.OverlayContainer;
import pro.gravit.launcher.client.gui.overlay.ProcessingOverlay;
import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.managers.SettingsManager;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.websockets.StandartClientWebSocketService;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class JavaFXApplication extends Application {
    public NewLauncherSettings settings;
    public StandartClientWebSocketService service;
    public AsyncRequestHandler requestHandler;
    public OverlayContainer overlays;
    public RuntimeStateMachine runtimeStateMachine;
    private SettingsManager settingsManager;
    private FXMLProvider fxmlProvider;
    private Stage mainStage;
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
        overlays = new OverlayContainer();
        runtimeStateMachine = new RuntimeStateMachine();
    }

    @Override
    public void start(Stage stage) throws Exception {
        // System loading
        fxmlProvider = new FXMLProvider(JavaFXApplication::newFXMLLoader, executors);
        //
        queueFxml("scenes/login/login.fxml");
        //Overlay loading
        overlays = new OverlayContainer();
        overlays.processingOverlay = new ProcessingOverlay(this);
        //
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        mainStage = stage;
        Scene loginScene = new Scene(fxmlProvider.getFxml("scenes/login/login.fxml"));
        loginScene.setFill(Color.TRANSPARENT);
        setScene(loginScene);
        LoginScene loginScene1 = new LoginScene(loginScene, stage,  this);
        loginScene1.init();
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
    public void setScene(Scene scene)
    {
        mainStage.setScene(scene);
        mainStage.sizeToScene();
        mainStage.show();
    }
}