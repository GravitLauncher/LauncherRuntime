package pro.gravit.launcher.client.gui.raw;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.AllowDisable;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractScene implements AllowDisable {
    protected Scene scene;
    public final Stage stage;
    public final JavaFXApplication application;
    public final String name;
    protected final LauncherConfig launcherConfig;
    protected final ContextHelper contextHelper;
    private Node currentOverlayNode;
    private AbstractOverlay currentOverlay;

    protected AbstractScene(String name, Stage stage, JavaFXApplication application) {
        this.name = name;
        this.stage = stage;
        this.application = application;
        this.launcherConfig = Launcher.getConfig();
        this.contextHelper = new ContextHelper(this);
    }

    public void init() throws Exception
    {
        if(scene == null)
        {
            scene = new Scene(application.getFxml(name));
            scene.setFill(Color.TRANSPARENT);
        }
        doInit();
    }

    protected abstract void doInit() throws Exception;

    void fade(Node region, double delay, double from, double to, EventHandler<ActionEvent> onFinished)
    {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), region);
        if(onFinished != null)
            fadeTransition.setOnFinished(onFinished);
        fadeTransition.setDelay(Duration.millis(delay));
        fadeTransition.setFromValue(from);
        fadeTransition.setToValue(to);
        fadeTransition.play();
    }
    public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished)
    {
        currentOverlay = overlay;
        if(!overlay.isInit) {
            try {
                overlay.init();
            } catch (IOException | InterruptedException e) {
                contextHelper.errorHandling(e);
                return;
            }
        }
        showOverlay(overlay.getPane(), onFinished);
    }
    private void showOverlay(Pane newOverlay, EventHandler<ActionEvent> onFinished)
    {
        if(newOverlay == null) throw new NullPointerException();
        if(currentOverlayNode != null) {
            swapOverlay(0, newOverlay, onFinished);
            return;
        }
        disable();
        currentOverlayNode = newOverlay;
        Pane root = (Pane) scene.getRoot();
        root.getChildren().get(0).setEffect(new GaussianBlur(10));
        fade(root, 0.0, 0.0, 1.0, (e) -> {
            root.getChildren().add(newOverlay);
            newOverlay.setLayoutX((root.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
            newOverlay.setLayoutY((root.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);
            newOverlay.toFront();
            newOverlay.requestFocus();
            fade(newOverlay, 0.0, 0.0, 1.0, onFinished);
        });
    }
    public void hideOverlay(double delay, EventHandler<ActionEvent> onFinished)
    {
        if(currentOverlayNode == null)
            return;
        enable();
        currentOverlay.reset();
        currentOverlay = null;
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlayNode, delay, 1.0, 0.0, (e) -> {
            root.getChildren().remove(currentOverlayNode);
            root.requestFocus();
            root.getChildren().get(0).setEffect(new GaussianBlur(0));
            currentOverlayNode = null;
            if (onFinished != null) {
                onFinished.handle(e);
            }
        });
    }
    private void swapOverlay(double delay, Pane newOverlay, EventHandler<ActionEvent> onFinished)
    {
        if(currentOverlayNode == null)
            throw new IllegalStateException("Try swap null overlay");
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlayNode, delay, 1.0, 0.0, (e) -> {
            if (currentOverlayNode != newOverlay)
            {
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
    public final<T extends WebSocketEvent> void processRequest(String message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onError);
    }
    public final<T extends WebSocketEvent> void processRequest(ObservableValue<String> message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onError);
    }

    public AbstractOverlay getCurrentOverlay() {
        return currentOverlay;
    }
    @Override
    public void disable() {

    }

    @Override
    public void enable() {

    }

    ContextHelper getContextHelper() {
        return contextHelper;
    }

    public Scene getScene() {
        return scene;
    }
    protected void sceneBaseInit(Node node)
    {
        ((ButtonBase)node.lookup("#close") ).setOnAction((e) -> {
            Platform.exit();
        });
        ((ButtonBase)node.lookup("#hide") ).setOnAction((e) -> {
            stage.setIconified(true);
        });
        AtomicReference<Point2D> movePoint = new AtomicReference<>();
        node.setOnMousePressed(event -> { movePoint.set(new Point2D(event.getSceneX(), event.getSceneY())); });
        node.setOnMouseDragged(event -> {
        if (movePoint.get() == null) {
            return;
        }
        stage.setX(event.getScreenX() - movePoint.get().getX());
        stage.setY(event.getScreenY() - movePoint.get().getY());
    });
    }
}
