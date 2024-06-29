package pro.gravit.launcher.gui.basic;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.helper.EnFSHelper;
import pro.gravit.launcher.gui.scenes.LoginScene;
import pro.gravit.launcher.gui.scenes.ServerMenuScene;
import pro.gravit.launcher.runtime.debug.DebugMain;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class FXApplication extends Application {
    private static volatile FXApplication APPLICATION;
    private final Map<String, Layer> allLayers = new HashMap<>();
    private FxStage primaryStage;
    private Path runtimeDir;
    private ResourceBundle resources;
    private String locale = "ru";

    public FXApplication() {
        super();
        APPLICATION = this;
    }

    public static FXApplication getInstance() {
        return APPLICATION;
    }

    @Override
    public void start(Stage stage) throws Exception {
        EnFSHelper.initEnFS();
        reload();
        primaryStage = new FxStage(stage);
        LauncherBackendAPIHolder.getApi().setCallback(new MainCallbackImpl());
        {
            Scenes.init();
            primaryStage.pushLayer(LayerPositions.SCENE, Scenes.LOGIN);
        }
        primaryStage.show();
    }

    public void reload() throws IOException {
        Path realDirectory = tryGetRealDirectory();
        runtimeDir = EnFSHelper.initEnFSDirectory(Launcher.getConfig(), "default", realDirectory);
        try (InputStream input = getResource("runtime_%s.properties".formatted(locale)).openStream()) {
            resources = new PropertyResourceBundle(input);
        }
    }

    public<T extends Layer> T register(T layer) {
        var name = layer.getName();
        if(name == null) {
            name = layer.getClass().getSimpleName();
        }
        allLayers.put(name, layer);
        return layer;
    }

    private static Path tryGetRealDirectory() {
        Path realDirectory;
        try {
            realDirectory = DebugMain.IS_DEBUG.get() ? Path.of("runtime") : null;
        } catch (Throwable e) {
            realDirectory = null;
        }
        return realDirectory;
    }

    public URL getResource(String name) throws IOException {
        return EnFSHelper.getURL(runtimeDir.resolve(name).toString());
    }

    public FXMLLoader createFxmlLoader(String fxml) throws IOException {
        var resource = getResource(fxml);
        var loader = new FXMLLoader();
        loader.setResources(resources);
        loader.setClassLoader(FXApplication.class.getClassLoader());
        loader.setLocation(resource);
        loader.setCharset(StandardCharsets.UTF_8);
        return loader;
    }

    public FxStage getMainStage() {
        return primaryStage;
    }

    public class MainCallbackImpl extends LauncherBackendAPI.MainCallback {
        @Override
        public void onAuthorize(SelfUser selfUser) {
            Platform.runLater(() -> {
                var mainLayer = primaryStage.getLayer(LayerPositions.SCENE);
                if("login".equals(mainLayer.getName())) {
                    primaryStage.pushLayer(LayerPositions.SCENE, Scenes.SERVER_MENU);
                }
            });
        }
    }
}
