package pro.gravit.launcher.client.gui.raw;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.AllowDisable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
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

    public abstract void errorHandle(Throwable e);

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
