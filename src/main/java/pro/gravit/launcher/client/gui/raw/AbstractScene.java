package pro.gravit.launcher.client.gui.raw;

import javafx.animation.FadeTransition;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.interfaces.AllowDisable;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractScene implements AllowDisable {
    public final String fxmlPath;
    protected final JavaFXApplication application;
    protected final LauncherConfig launcherConfig;
    protected final ContextHelper contextHelper;
    protected Scene scene;
    protected Node layout;
    protected Node header;
    protected Pane disablePane;
    AbstractStage currentStage;
    private Node currentOverlayNode;
    private AbstractOverlay currentOverlay;
    private AtomicInteger enabled = new AtomicInteger(0);
    private boolean hideTransformStarted = false;

    protected AbstractScene(String fxmlPath, JavaFXApplication application) {
        this.fxmlPath = fxmlPath;
        this.application = application;
        this.launcherConfig = Launcher.getConfig();
        this.contextHelper = new ContextHelper(this);
    }

    public static void fade(Node region, double delay, double from, double to, EventHandler<ActionEvent> onFinished) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), region);
        if (onFinished != null)
            fadeTransition.setOnFinished(onFinished);
        fadeTransition.setDelay(Duration.millis(delay));
        fadeTransition.setFromValue(from);
        fadeTransition.setToValue(to);
        fadeTransition.play();
    }

    protected AbstractStage getCurrentStage() {
        return currentStage;
    }

    public void init() throws Exception {
        if (scene == null) {
            scene = new Scene(application.getFxml(fxmlPath));
            scene.setFill(Color.TRANSPARENT);
        }
        layout = LookupHelper.lookupIfPossible(scene.getRoot(), "#layout").orElse(scene.getRoot());
        header = LookupHelper.lookupIfPossible(layout, "#header").orElse(null);
        sceneBaseInit();
        doInit();
    }

    protected abstract void doInit() throws Exception;

    public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) {
        currentOverlay = overlay;
        if (!overlay.isInit) {
            try {
                overlay.init();
            } catch (IOException | InterruptedException e) {
                contextHelper.errorHandling(e);
                return;
            }
        }
        overlay.currentStage = currentStage;
        currentStage.enableMouseDrag(overlay.pane);
        showOverlay(overlay.getPane(), onFinished);
    }

    private void showOverlay(Pane newOverlay, EventHandler<ActionEvent> onFinished) {
        if (newOverlay == null) throw new NullPointerException();
        if (currentOverlayNode != null) {
            swapOverlay(newOverlay, onFinished);
            return;
        }
        currentOverlayNode = newOverlay;
        Pane root = (Pane) scene.getRoot();
        disable();
        root.getChildren().add(newOverlay);
        newOverlay.setLayoutX((root.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
        newOverlay.setLayoutY((root.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);
        newOverlay.toFront();
        newOverlay.requestFocus();
        fade(newOverlay, 0.0, 0.0, 1.0, onFinished);
    }

    public void hideOverlay(double delay, EventHandler<ActionEvent> onFinished) {
        if (currentOverlayNode == null)
            return;
        if (currentOverlay == null)
            return;
        if(hideTransformStarted) {
            if(onFinished != null) {
                contextHelper.runInFxThread(() -> onFinished.handle(null));
            }
        }
        hideTransformStarted = true;
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlayNode, delay, 1.0, 0.0, (e) -> {
            root.getChildren().remove(currentOverlayNode);
            root.requestFocus();
            enable();
            currentOverlayNode = null;
            if (currentOverlay != null) currentOverlay.currentStage = null;
            if (currentOverlay != null) currentOverlay.reset();
            currentOverlay = null;
            if (onFinished != null) {
                onFinished.handle(e);
            }
            hideTransformStarted = false;
        });
    }

    private void swapOverlay(Pane newOverlay, EventHandler<ActionEvent> onFinished) {
        if (currentOverlayNode == null)
            throw new IllegalStateException("Try swap null overlay");
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlayNode, 0, 1.0, 0.0, (e) -> {
            if (currentOverlayNode != newOverlay) {
                ObservableList<Node> child = root.getChildren();
                child.set(child.indexOf(currentOverlayNode), newOverlay);
            }
            newOverlay.setLayoutX((root.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
            newOverlay.setLayoutY((root.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);
            currentOverlayNode = newOverlay;
            newOverlay.toFront();
            newOverlay.requestFocus();
            fade(newOverlay, 0.0, 0.0, 1.0, onFinished);
        });
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onError);
    }
    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request, Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onException, onError);
    }

    public AbstractOverlay getCurrentOverlay() {
        return currentOverlay;
    }

    @Override
    public void disable() {
        LogHelper.debug("Scene %s disabled (%d)", getName(), enabled.incrementAndGet());
        if(enabled.get() != 1) return;
        Pane root = (Pane) scene.getRoot();
        if(layout == root) {
            throw new IllegalStateException("AbstractScene.disable() failed: layout == root");
        }
        layout.setEffect(new GaussianBlur(10));
        if(disablePane == null) {
            disablePane = new Pane();
            int index = root.getChildren().indexOf(layout);
            root.getChildren().add(index+1, disablePane);
            disablePane.setVisible(true);
        }
    }

    @Override
    public void enable() {
        LogHelper.debug("Scene %s enabled (%d)", getName(), enabled.decrementAndGet());
        if(enabled.get() != 0) return;
        layout.setEffect(new GaussianBlur(0));
        disablePane.setVisible(false);
    }

    public boolean isEnabled() {
        return enabled.get() == 0;
    }

    protected void doShow() {

    }

    public abstract void reset();

    public abstract void errorHandle(Throwable e);

    public Scene getScene() {
        return scene;
    }

    private void sceneBaseInit() {
        if(header == null) {
            LogHelper.warning("Scene %s header button(#close, #hide) deprecated", getName());
            LookupHelper.<ButtonBase>lookupIfPossible(layout,  "#close").ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(layout,  "#hide").ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
        } else {
            LookupHelper.<ButtonBase>lookupIfPossible(header,  "#controls", "#exit").ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(header,  "#controls", "#minimize").ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
        }
        currentStage.enableMouseDrag(layout);
    }

    public Node getLayout() {
        return layout;
    }

    public Node getHeader() {
        return header;
    }

    public static void runLater(double delay, EventHandler<ActionEvent> callback) {
        fade(null, delay, 0.0, 1.0, callback);
    }

    public abstract String getName();
}
