package pro.gravit.launcher.client.gui.impl;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.AllowDisable;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractOverlay implements AllowDisable {
    protected final JavaFXApplication application;
    protected AbstractStage currentStage;
    protected Pane pane;
    private final CompletableFuture<Node> future;
    boolean isInit;

    protected AbstractOverlay(String fxmlPath, JavaFXApplication application) {
        this.application = application;
        this.future = application.fxmlFactory.getAsync(fxmlPath);
    }

    public final void init() throws IOException, InterruptedException, ExecutionException {
        pane = (Pane) future.get();
        doInit();
        isInit = true;
    }

    protected final void hide(double delay, AbstractScene scene, EventHandler<ActionEvent> onFinished) {
        if (!isInit)
            throw new IllegalStateException("Using method hide before init");
        scene.hideOverlay(delay, (e) -> {
            if (onFinished != null)
                onFinished.handle(e);
        });
    }

    protected abstract void doInit();

    public abstract void reset();

    public void errorHandle(Throwable e) {
        String message = null;
        if(e instanceof CompletionException) {
            e = e.getCause();
        }
        if(e instanceof ExecutionException) {
            e = e.getCause();
        }
        if(e instanceof RequestException) {
            message = e.getMessage();
        }
        if(message == null) {
            message = String.format("%s: %s", e.getClass().getName(), e.getMessage());
        }
        LogHelper.error(e);
        application.messageManager.createNotification("Error", message);
    }

    public Pane getPane() {
        return pane;
    }

    @Override
    public void disable() {
    }

    @Override
    public void enable() {
    }
}
