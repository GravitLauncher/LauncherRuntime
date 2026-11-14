package pro.gravit.launcher.gui.core.impl;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.LauncherConfig;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.components.BasicUserControls;
import pro.gravit.launcher.gui.helper.LookupHelper;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public abstract class FxScene extends FxComponent {
    protected final LauncherConfig launcherConfig;
    protected Pane header;
    protected BasicUserControls basicUserControls;
    protected BiFunction<Pane, JavaFXApplication, ? extends BasicUserControls> _basicUserControlConstructor = BasicUserControls::new;

    protected FxScene(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
        this.launcherConfig = Launcher.getConfig();
    }

    protected FxStage getCurrentStage() {
        return currentStage;
    }

    public void init() throws Exception {
        if (isInit()) return;
        layout = (Pane) getFxmlRoot();
        header = (Pane) LookupHelper.lookupIfPossible(layout, Pane.class, "#header").orElse(null);
        sceneBaseInit();
        super.init();
    }

    protected abstract void doInit();

    protected void sceneBaseInit() {
        basicUserControls = use(header, _basicUserControlConstructor);
        currentStage.enableMouseDrag(header);
    }

    public void disable() {
        currentStage.disable();
    }

    public void enable() {
        currentStage.enable();
    }

    public abstract void reset();

    public Node getHeader() {
        return header;
    }

    public static void runLater(double delay, EventHandler<ActionEvent> callback) {
        var timer = new javafx.animation.PauseTransition(javafx.util.Duration.millis(delay));
        timer.setOnFinished(callback); timer.play();
    }

    public class SceneAccessor {
        public SceneAccessor() {
        }


        public void showOverlay(FxOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
            FxScene.this.showOverlay(overlay, onFinished);
        }

        public JavaFXApplication getApplication() {
            return application;
        }

        public void errorHandle(Throwable e) {
            FxScene.this.errorHandle(e);
        }

        public void runInFxThread(ContextHelper.GuiExceptionRunnable runnable) {
            contextHelper.runInFxThread(runnable);
        }

        public <T> void processRequest(String message, CompletableFuture<T> request,
                Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
            FxScene.this.processRequest(message, request, onSuccess, onError);
        }

        public final <T> void processRequest(String message, CompletableFuture<T> request,
                Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
            FxScene.this.processRequest(message, request, onSuccess, onException, onError);
        }
    }
}
