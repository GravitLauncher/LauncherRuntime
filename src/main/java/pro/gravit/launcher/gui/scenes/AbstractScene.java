package pro.gravit.launcher.gui.scenes;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.LauncherConfig;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.impl.AbstractStage;
import pro.gravit.launcher.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.gui.impl.ContextHelper;
import pro.gravit.launcher.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.base.request.Request;
import pro.gravit.launcher.base.request.WebSocketEvent;
import pro.gravit.launcher.base.request.auth.ExitRequest;

import java.util.function.Consumer;

public abstract class AbstractScene extends AbstractVisualComponent {
    protected final LauncherConfig launcherConfig;
    protected Pane header;

    protected AbstractScene(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
        this.launcherConfig = Launcher.getConfig();
    }

    protected AbstractStage getCurrentStage() {
        return currentStage;
    }

    public void init() throws Exception {
        layout = (Pane) getFxmlRoot();
        header = (Pane) LookupHelper.lookupIfPossible(layout, "#header").orElse(null);
        sceneBaseInit();
        super.init();
    }

    protected abstract void doInit();

    @Override
    protected void doPostInit() {

    }

    protected void onShow() {

    }

    protected void onHide() {

    }


    public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
        overlay.show(currentStage, onFinished);
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request,
            Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(currentStage, message, request, onSuccess, onError);
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request,
            Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(currentStage, message, request, onSuccess, onException, onError);
    }

    protected void sceneBaseInit() {
        initBasicControls(header);
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#deauth").ifPresent(b -> b.setOnAction(
                (e) -> application.messageManager.showApplyDialog(
                        application.getTranslation("runtime.scenes.settings.exitDialog.header"),
                        application.getTranslation("runtime.scenes.settings.exitDialog.description"),
                        this::userExit, () -> {}, true)));
    }

    protected void userExit() {
        processRequest(application.getTranslation("runtime.scenes.settings.exitDialog.processing"), new ExitRequest(),
                       (event) -> {
                           // Exit to main menu
                           ContextHelper.runInFxThreadStatic(() -> {
                               application.gui.loginScene.clearPassword();
                               application.gui.loginScene.reset();
                               try {
                                   application.saveSettings();
                                   application.authService.exit();
                                   switchScene(application.gui.loginScene);
                               } catch (Exception ex) {
                                   errorHandle(ex);
                               }
                           });
                       }, (event) -> {});
    }

    protected void switchToBackScene() throws Exception {
        currentStage.back();
    }

    public void disable() {
        currentStage.disable();
    }

    public void enable() {
        currentStage.enable();
    }

    public abstract void reset();

    protected void switchScene(AbstractScene scene) throws Exception {
        currentStage.setScene(scene, true);
        onHide();
        scene.onShow();
    }

    public Node getHeader() {
        return header;
    }

    public static void runLater(double delay, EventHandler<ActionEvent> callback) {
        fade(null, delay, 0.0, 1.0, callback);
    }

    public class SceneAccessor {
        public SceneAccessor() {
        }


        public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
            AbstractScene.this.showOverlay(overlay, onFinished);
        }

        public JavaFXApplication getApplication() {
            return application;
        }

        public void errorHandle(Throwable e) {
            AbstractScene.this.errorHandle(e);
        }

        public void runInFxThread(ContextHelper.GuiExceptionRunnable runnable) {
            contextHelper.runInFxThread(runnable);
        }

        public <T extends WebSocketEvent> void processRequest(String message, Request<T> request,
                Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
            AbstractScene.this.processRequest(message, request, onSuccess, onError);
        }

        public final <T extends WebSocketEvent> void processRequest(String message, Request<T> request,
                Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
            AbstractScene.this.processRequest(message, request, onSuccess, onException, onError);
        }
    }
}
