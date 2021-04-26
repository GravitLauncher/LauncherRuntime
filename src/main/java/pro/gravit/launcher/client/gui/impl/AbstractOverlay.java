package pro.gravit.launcher.client.gui.impl;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractOverlay extends AbstractVisualComponent {

    protected AbstractOverlay(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
    }

    public final void init() throws IOException, InterruptedException, ExecutionException {
        layout = (Pane) getFxmlRoot();
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

    public void disable() {
    }

    public void enable() {
    }
}
