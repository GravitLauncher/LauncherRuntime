package pro.gravit.launcher.gui.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.LauncherConfig;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.JavaRuntimeModule;
import pro.gravit.launcher.gui.core.commands.RuntimeCommand;
import pro.gravit.launcher.gui.core.commands.VersionCommand;
import pro.gravit.launcher.gui.core.config.GuiModuleConfig;
import pro.gravit.launcher.gui.core.config.RuntimeSettings;
import pro.gravit.launcher.gui.core.internal.FXMLFactory;
import pro.gravit.launcher.gui.core.impl.GuiObjectsContainer;
import pro.gravit.launcher.gui.core.impl.MessageManager;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.gui.core.service.*;
import pro.gravit.launcher.gui.core.utils.SystemTheme;
import pro.gravit.launcher.gui.stage.PrimaryStage;
import pro.gravit.launcher.runtime.LauncherEngine;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.launcher.runtime.client.events.ClientGuiPhase;
import pro.gravit.launcher.runtime.managers.ConsoleManager;
import pro.gravit.utils.command.BaseCommandCategory;
import pro.gravit.utils.command.CommandCategory;
import pro.gravit.utils.command.CommandHandler;
import pro.gravit.utils.helper.IOHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class JavaFXApplication extends Application {

    private static final Logger logger =
            LoggerFactory.getLogger(JavaFXApplication.class);

    private static final AtomicReference<JavaFXApplication> INSTANCE = new AtomicReference<>();
    public final LauncherConfig config = Launcher.getConfig();
    public final ExecutorService workers = Executors.newWorkStealingPool(4);
    private final pro.gravit.launcher.gui.core.ui.UiScope applicationScope = new pro.gravit.launcher.gui.core.ui.UiScope();
    public RuntimeSettings runtimeSettings;
    public GuiObjectsContainer gui;
    public AuthService authService;
    public ProfileService profileService;
    public GuiModuleConfig guiModuleConfig;
    public MessageManager messageManager;
    public SkinManager skinManager;
    public FXMLFactory fxmlFactory;
    public PingService pingService;
    public ServerButtonStateService serverButtonStateService;
    public ServerCardService serverCardService;
    public BackendCallbackService backendCallbackService;
    private PrimaryStage mainStage;
    private boolean debugMode;
    private ResourceBundle resources;
    private static volatile LauncherBackendAPI.ResourceLayer resourceLayer;
    private AtomicBoolean isFirstStart = new AtomicBoolean();
    private final AtomicReference<UiActionTrace> pendingUiAction = new AtomicReference<>();

    public JavaFXApplication() {
        INSTANCE.set(this);
    }

    public static JavaFXApplication getInstance() {
        return INSTANCE.get();
    }

    public FxScene getCurrentScene() {
        return mainStage == null ? null : (FxScene) mainStage.getVisualComponent();
    }

    public PrimaryStage getMainStage() {
        return mainStage;
    }

    @Override
    public void init() throws Exception {
        guiModuleConfig = new GuiModuleConfig();
        authService = new AuthService(this);
        profileService = new ProfileService(this);
        messageManager = new MessageManager(this);
        skinManager = new SkinManager(this);
        pingService = new PingService();
        serverButtonStateService = new ServerButtonStateService(this, pingService);
        serverCardService = new ServerCardService(this);
        var backend = LauncherBackendAPIHolder.getApi();
        backend.registerUserSettings(JavaRuntimeModule.RUNTIME_NAME, RuntimeSettings.class);
        backendCallbackService = new BackendCallbackService(this);
        backend.setCallback(backendCallbackService);
        backendCallbackService.initDataCallback = backend.init();
        if (backendCallbackService.initDataCallback.isCompletedExceptionally()) backendCallbackService.initDataCallback.join();
        runtimeSettings = (RuntimeSettings) backend.getUserSettings(JavaRuntimeModule.RUNTIME_NAME, name -> {
            isFirstStart.set(true);
            return RuntimeSettings.getDefault(guiModuleConfig);
        });
        runtimeSettings.apply();
        System.setProperty("prism.vsync", String.valueOf(runtimeSettings.globalSettings.prismVSync));
        DirBridge.dirUpdates = runtimeSettings.updatesDir == null ? DirBridge.defaultUpdatesDir : runtimeSettings.updatesDir;
        registerCommands();
    }

    @Override
    public void start(Stage stage) {
        debugMode = LauncherBackendAPIHolder.getApi().isTestMode();
        if(isFirstStart.get() || runtimeSettings.theme == null) {
            try {
                runtimeSettings.theme = SystemTheme.getSystemTheme();
            } catch (Throwable e) {
                logger.error("Error when getting system theme", e);
            }
        }
        resetDirectory();
        // System loading
        if (runtimeSettings.locale == null) runtimeSettings.locale = RuntimeSettings.DEFAULT_LOCALE;
        try {
            updateLocaleResources(runtimeSettings.locale.name);
        } catch (Throwable e) {
            JavaRuntimeModule.noLocaleAlert(runtimeSettings.locale.name);
            if (!(e instanceof FileNotFoundException)) {
                logger.error("", e);
            }
            Platform.exit();
            return;
        }
        try {
            mainStage = new PrimaryStage(this, stage, "%s Launcher".formatted(config.projectName));
            // Overlay loading
            gui = new GuiObjectsContainer(this);
            gui.init();
            //
            mainStage.setScene(gui.loginScene, true);
            gui.background.init();
            mainStage.pushBackground(gui.background);
            mainStage.show();
            messageManager.registerPublicApi();
            pro.gravit.launcher.gui.core.ui.ModuleEvents.listen(applicationScope,
                    LauncherEngine.modulesManager.getEventBus(),
                    pro.gravit.launcher.client.events.ClientUnlockConsoleEvent.class,
                    event -> registerPrivateCommands(), error -> logger.error("Console unlock failed", error));
                //
            LauncherEngine.modulesManager.invokeEvent(new ClientGuiPhase(StdJavaRuntimeProvider.getInstance()));
        } catch (Throwable e) {
            logger.error("", e);
            JavaRuntimeModule.errorHandleAlert(e);
            Platform.exit();
        }
    }

    public void updateLocaleResources(String locale) throws IOException {
        try (InputStream input = getResource("runtime_%s.properties".formatted(locale))) {
            resources = new PropertyResourceBundle(input);
        }
        fxmlFactory = new FXMLFactory(resources);
    }

    public void resetDirectory() {
        String themeDir = runtimeSettings.theme == null ? RuntimeSettings.LAUNCHER_THEME.COMMON.name :
                runtimeSettings.theme.name;
        resourceLayer = LauncherBackendAPIHolder.getApi().makeResourceLayer(themeDir == null ? List.of() : List.of(Path.of("themes/" + themeDir)));
        serverButtonStateService = new ServerButtonStateService(this, pingService);
        serverCardService = new ServerCardService(this);
    }

    public record ResourceState(LauncherBackendAPI.ResourceLayer layer, ResourceBundle resources, FXMLFactory factory, ServerButtonStateService states, ServerCardService cards) { }

    public ResourceState captureResources() { return new ResourceState(resourceLayer, resources, fxmlFactory, serverButtonStateService, serverCardService); }
    public void restoreResources(ResourceState state) {
        resourceLayer = state.layer(); resources = state.resources(); fxmlFactory = state.factory();
        serverButtonStateService = state.states(); serverCardService = state.cards();
    }
    public void releaseResources(ResourceState state) {
        if (state.cards() != null) state.cards().clear();
        if (state.states() != null) state.states().clear();
        if (state.layer() == null) return;
        try { state.layer().close(); }
        catch (Exception error) { logger.warn("Resource layer cleanup failed", error); }
    }
    private final javafx.beans.property.ReadOnlyIntegerWrapper operations = new javafx.beans.property.ReadOnlyIntegerWrapper();
    public javafx.beans.value.ObservableValue<Boolean> operationBusyProperty() { return operations.map(count -> count.intValue() > 0); }
    public boolean isOperationBusy() { return operations.get() > 0 || (gui != null && gui.debugScene.isRunning()); }
    public void beginOperation() { pro.gravit.launcher.gui.core.ui.FxDispatcher.checkThread(); operations.set(operations.get() + 1); }
    public void endOperation() { pro.gravit.launcher.gui.core.ui.FxDispatcher.checkThread(); operations.set(Math.max(0, operations.get() - 1)); }

    private void comDialogCleanup() {
        pro.gravit.launcher.client.api.DialogService.setDialogImpl(null);
        pro.gravit.launcher.client.api.DialogService.setNotificationImpl(null);
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
        logger.debug("JavaFX method stop invoked");
        applicationScope.close();
        if (gui != null) gui.dispose();
        if (serverCardService != null) serverCardService.clear();
        if (serverButtonStateService != null) serverButtonStateService.clear();
        comDialogCleanup();
        workers.shutdownNow();
        releaseResources(captureResources());
        LauncherEngine.modulesManager.invokeEvent(new ClientExitPhase(0));
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private InputStream getResource(String name) throws IOException {
        return IOHelper.newInput(getResourceURL(name));
    }

    public static URL getResourceURL(String name) throws IOException {
        return resourceLayer.getURL(Path.of(name));
    }

    public URL tryResource(String name) {
        try {
            return getResourceURL(name);
        } catch (IOException e) {
            return null;
        }

    }

    public void setMainScene(FxScene scene) throws Exception {
        mainStage.setScene(scene, true);
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
        return getTranslation(name, "'%s'".formatted(name));
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
            if (pro.gravit.utils.helper.JVMHelper.OS_TYPE != pro.gravit.utils.helper.JVMHelper.OS.MUSTDIE) {
                var clipboard = new javafx.scene.input.ClipboardContent();
                clipboard.putString(url);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboard);
                messageManager.createNotification(getTranslation("runtime.linkCopied", "Link or path copied"), "");
                return true;
            }
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null) uri = Path.of(url).toUri();
            getHostServices().showDocument(uri.toString());
            return true;
        } catch (Throwable e) {
            logger.error("", e);
            return false;
        }
    }

    public void beginUiAction(String action, String sourceScene, String targetScene, String state) {
        UiActionTrace trace = new UiActionTrace(action, sourceScene, targetScene, state, System.nanoTime());
        pendingUiAction.set(trace);
        logger.info("UiAction start action={} sourceScene={} targetScene={} state={} thread={}",
                action, sourceScene, targetScene, state, Thread.currentThread().getName());
    }

    public void logUiActionPhase(String phase, String details) {
        UiActionTrace trace = pendingUiAction.get();
        if (trace == null) {
            return;
        }
        logger.info("UiAction phase action={} sourceScene={} targetScene={} phase={} elapsedMs={} details={}",
                trace.action(), trace.sourceScene(), trace.targetScene(), phase,
                formatMs(System.nanoTime() - trace.startedAtNanos()), details);
    }

    public void finishUiActionForScene(String sceneName, String details) {
        UiActionTrace trace = pendingUiAction.get();
        if (trace == null) {
            return;
        }
        if (trace.targetScene() != null && !trace.targetScene().equals(sceneName)) {
            return;
        }
        if (pendingUiAction.compareAndSet(trace, null)) {
            logger.info("UiAction finish action={} sourceScene={} targetScene={} displayedScene={} elapsedMs={} details={}",
                    trace.action(), trace.sourceScene(), trace.targetScene(), sceneName,
                    formatMs(System.nanoTime() - trace.startedAtNanos()), details);
        }
    }

    private static String formatMs(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private record UiActionTrace(String action, String sourceScene, String targetScene, String state,
                                 long startedAtNanos) {
    }
}
