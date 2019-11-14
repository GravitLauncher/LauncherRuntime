package pro.gravit.launcher.client.gui.overlay;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.AllowDisable;
import pro.gravit.launcher.client.gui.scene.AbstractScene;

import java.io.IOException;

public abstract class AbstractOverlay implements AllowDisable {
    protected final JavaFXApplication application;
    protected final String name;
    protected Pane pane;
    private boolean isInit;
    protected AbstractOverlay(String name, JavaFXApplication application) throws IOException {
        this.application = application;
        this.name = name;
        application.queueFxml(name);
    }
    public final void init() throws IOException, InterruptedException {
        pane = application.getFxml(name);
        doInit();
        isInit = true;
    }
    public final void show(AbstractScene scene, EventHandler<ActionEvent> onFinished) throws IOException, InterruptedException {
        if(!isInit)
            init();
        scene.showOverlay(this, onFinished);
    }
    public final void hide(double delay, AbstractScene scene, EventHandler<ActionEvent> onFinished) {
        if(!isInit)
            throw new IllegalStateException("Using method hide before init");
        scene.hideOverlay(delay, (e) -> {
            reset();
            if(onFinished != null)
                onFinished.handle(e);
        });
    }
    protected abstract void doInit() throws IOException;
    public abstract void reset();

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
