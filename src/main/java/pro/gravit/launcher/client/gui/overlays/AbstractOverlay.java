package pro.gravit.launcher.client.gui.overlays;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.impl.AbstractStage;
import pro.gravit.launcher.client.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;

public abstract class AbstractOverlay extends AbstractVisualComponent {

    protected AbstractOverlay(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
    }

    public final void init() throws Exception {
        super.init();
    }

    protected final void hide(double delay, AbstractScene scene, EventHandler<ActionEvent> onFinished) {
        if (!isInit())
            throw new IllegalStateException("Using method hide before init");
        scene.hideOverlay(delay, (e) -> {
            if (onFinished != null)
                onFinished.handle(e);
        });
    }

    protected abstract void doInit();

    public abstract void reset();

    public void disable() {
    }

    public void enable() {
    }

    public Pane show(AbstractStage stage) throws Exception {
        if(!isInit()) {
            init();
        }
        this.currentStage = stage;
        currentStage.enableMouseDrag(layout);
        return layout;
    }
}
