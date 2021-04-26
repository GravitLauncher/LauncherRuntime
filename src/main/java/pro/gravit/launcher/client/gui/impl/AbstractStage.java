package pro.gravit.launcher.client.gui.impl;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;

import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractStage {
    public final Stage stage;
    protected AbstractVisualComponent scene;

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

    public AbstractVisualComponent getVisualComponent() {
        return scene;
    }

    public void setScene(AbstractVisualComponent visualComponent) throws Exception {
        if (visualComponent == null) {
            throw new NullPointerException("Try set null scene");
        }
        visualComponent.currentStage = this;
        if(!visualComponent.isInit()) {
            visualComponent.init();
        }
        if(visualComponent.isResetOnShow) {
            visualComponent.reset();
        }
        if(visualComponent instanceof AbstractScene) {
            stage.setScene(((AbstractScene) visualComponent).getScene());
        } else {
            Scene scene = new Scene(visualComponent.layout);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
        }
        stage.sizeToScene();
        this.scene = visualComponent;
    }

    public final boolean isNullScene() {
        return scene == null;
    }

    public void show() {
        stage.show();
    }
}
