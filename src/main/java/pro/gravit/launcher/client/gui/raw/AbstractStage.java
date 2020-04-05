package pro.gravit.launcher.client.gui.raw;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractStage {
    public final Stage stage;
    protected AbstractScene scene;

    protected AbstractStage(Stage stage) {
        this.stage = stage;
    }

    public void hide() {
        stage.setIconified(true);
    }

    public void close() {
        stage.hide();
    }

    public void enableMouseDrag(Node node) {
        AtomicReference<Point2D> movePoint = new AtomicReference<>();
        node.setOnMousePressed(event -> movePoint.set(new Point2D(event.getSceneX(), event.getSceneY())));
        node.setOnMouseDragged(event -> {
            if (movePoint.get() == null) {
                return;
            }
            stage.setX(event.getScreenX() - movePoint.get().getX());
            stage.setY(event.getScreenY() - movePoint.get().getY());
        });
    }

    public AbstractScene getScene() {
        return scene;
    }

    public void setScene(AbstractScene scene) throws Exception {
        if (scene == null) {
            throw new NullPointerException("Try set null scene");
        }
        scene.currentStage = this;
        if (scene.getScene() == null)
            scene.init();
        stage.setScene(scene.getScene());
        stage.sizeToScene();
        stage.show();
        this.scene = scene;
    }

    public final boolean isNullScene() {
        return scene == null;
    }

    public void show() {
        stage.show();
    }
}
