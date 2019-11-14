package pro.gravit.launcher.client.gui.scene;

import javafx.animation.FadeTransition;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.function.Consumer;

public abstract class AbstractScene {
    public final Scene scene;
    public final Stage stage;
    public final JavaFXApplication application;
    protected Node currentOverlay;

    protected AbstractScene(Scene scene, Stage stage, JavaFXApplication application) {
        this.scene = scene;
        this.stage = stage;
        this.application = application;
    }

    abstract void init() throws Exception;

    protected void fade(Node region, double delay, double from, double to, EventHandler<ActionEvent> onFinished)
    {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), region);
        if(onFinished != null)
            fadeTransition.setOnFinished(onFinished);
        fadeTransition.setDelay(Duration.millis(delay));
        fadeTransition.setFromValue(from);
        fadeTransition.setToValue(to);
        fadeTransition.play();
    }
    public void showOverlay(Pane newOverlay, EventHandler<ActionEvent> onFinished)
    {
        if(currentOverlay != null) {
            swapOverlay(0, newOverlay, onFinished);
            return;
        }
        currentOverlay = newOverlay;
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
        if(currentOverlay == null)
            return;
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlay, delay, 1.0, 0.0, (e) -> {
            root.getChildren().remove(currentOverlay);
            root.requestFocus();
            root.getChildren().get(0).setEffect(new GaussianBlur(0));
            currentOverlay = null;
            if (onFinished != null) {
                onFinished.handle(e);
            }
        });
    }
    private void swapOverlay(double delay, Pane newOverlay, EventHandler<ActionEvent> onFinished)
    {
        if(currentOverlay == null)
            throw new IllegalStateException("Try swap null overlay");
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlay, delay, 1.0, 0.0, (e) -> {
            if (currentOverlay != newOverlay)
            {
                ObservableList<Node> child = root.getChildren();
                child.set(child.indexOf(currentOverlay), newOverlay);
            }
            newOverlay.setLayoutX((root.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
            newOverlay.setLayoutY((root.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);
            currentOverlay = newOverlay;
            newOverlay.toFront();
            newOverlay.requestFocus();
            fade(newOverlay, 0.0, 0.0, 1.0, onFinished);
        });
    }
    public final<T extends WebSocketEvent> void processRequest(String message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.overlays.processingOverlay.processRequest(message, this, request, onSuccess, onError);
    }
    public final<T extends WebSocketEvent> void processRequest(ObservableValue<String> message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.overlays.processingOverlay.processRequest(message, this, request, onSuccess, onError);
    }
}
