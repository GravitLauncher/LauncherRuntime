package pro.gravit.launcher.client.gui.raw;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.AllowDisable;

import java.io.IOException;

public abstract class AbstractOverlay implements AllowDisable {
    protected final JavaFXApplication application;
    public final String fxmlPath;
    protected Pane pane;
    boolean isInit;

    protected AbstractOverlay(String fxmlPath, JavaFXApplication application) {
        this.application = application;
        this.fxmlPath = fxmlPath;
    }

    public final void init() throws IOException, InterruptedException {
        pane = application.getFxml(fxmlPath);
        doInit();
        isInit = true;
    }

    public final void show(AbstractScene scene, EventHandler<ActionEvent> onFinished) throws IOException, InterruptedException {
        if (!isInit)
            init();
        scene.showOverlay(this, onFinished);
    }

    public final void hide(double delay, AbstractScene scene, EventHandler<ActionEvent> onFinished) {
        if (!isInit)
            throw new IllegalStateException("Using method hide before init");
        scene.hideOverlay(delay, (e) -> {
            reset();
            if (onFinished != null)
                onFinished.handle(e);
        });
    }

    protected abstract void doInit();

    public abstract void reset();

    public abstract void errorHandle(String error);

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
