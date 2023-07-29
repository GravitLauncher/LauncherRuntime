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

    public final void hide(double delay, EventHandler<ActionEvent> onFinished) {
        if (!isInit())
            throw new IllegalStateException("Using method hide before init");
        fade(getFxmlRoot(), delay, 1.0, 0.0, (f) -> {
            if(onFinished != null) {
                onFinished.handle(f);
            }
            currentStage.pull(getFxmlRoot());
            currentStage.enable();
        });
    }

    protected abstract void doInit();

    @Override
    protected void doPostInit() throws Exception {

    }

    public abstract void reset();

    public void disable() {
    }

    public void enable() {
    }

    public void show(AbstractStage stage, EventHandler<ActionEvent> onFinished) throws Exception {
        if (!isInit()) {
            init();
        }
        this.currentStage = stage;
        currentStage.enableMouseDrag(layout);
        currentStage.push(getFxmlRoot());
        currentStage.disable();
        fade(getFxmlRoot(), 100, 0.0, 1.0, (f) -> {
            if(onFinished != null) {
                onFinished.handle(f);
            }
        });
    }
}
