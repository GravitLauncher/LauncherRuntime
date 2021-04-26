package pro.gravit.launcher.client.gui.impl;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.utils.FXMLFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractVisualComponent {
    protected final JavaFXApplication application;
    protected final ContextHelper contextHelper;
    protected AbstractStage currentStage;
    protected Pane layout;
    private final CompletableFuture<Node> future;
    boolean isInit;

    protected AbstractVisualComponent(String fxmlPath, JavaFXApplication application) {
        this.application = application;
        this.future = application.fxmlFactory.getAsync(fxmlPath);
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

    public Pane getLayout() {
        return layout;
    }

    protected Parent getFxmlRoot() {
        try {
            return  (Parent) future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if(cause instanceof CompletionException) {
                cause = cause.getCause();
            }
            if(cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new FXMLFactory.FXMLLoadException(cause);
        }
    }
    public abstract void init() throws Exception;
    public abstract void reset();
    public abstract void disable();
    public abstract void enable();
    public abstract void errorHandle(Throwable e);
}
