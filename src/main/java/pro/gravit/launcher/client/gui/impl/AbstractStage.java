package pro.gravit.launcher.client.gui.impl;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractStage {
    protected final Stage stage;
    protected final Scene scene;
    protected final StackPane stackPane;
    protected AbstractVisualComponent visualComponent;
    protected Pane disablePane;
    protected VBox notificationsVBox;
    protected AnchorPane notifications;
    private final AtomicInteger disableCounter = new AtomicInteger(0);

    protected AbstractStage(Stage stage) {
        this.stage = stage;
        this.stackPane = new StackPane();
        this.scene = new Scene(stackPane);
        this.stage.setScene(scene);
        try {
            this.scene.getStylesheets().add(JavaFXApplication.getResourceURL("styles/global.css").toString());
        } catch (IOException e) {
            LogHelper.error(e);
        }
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
        return visualComponent;
    }

    public void setScene(AbstractVisualComponent visualComponent) throws Exception {
        if (visualComponent == null) {
            if(stackPane.getChildren().size() != 0) {
                stackPane.getChildren().set(0, new Pane());
            }
            return;
        }
        visualComponent.currentStage = this;
        if (!visualComponent.isInit()) {
            visualComponent.init();
        }
        if (visualComponent.isResetOnShow) {
            visualComponent.reset();
        }
        if (stackPane.getChildren().size() == 0) {
            stackPane.getChildren().add(visualComponent.getFxmlRoot());
        } else {
            stackPane.getChildren().set(0, visualComponent.getFxmlRoot());
        }
        stage.sizeToScene();
        visualComponent.postInit();
        this.visualComponent = visualComponent;
    }

    public void push(Node node) {
        stackPane.getChildren().add(node);
    }

    public boolean contains(Node node) {
        return stackPane.getChildren().contains(node);
    }

    public void pull(Node node) {
        stackPane.getChildren().remove(node);
    }

    public void addAfter(Node node, Node value) {
        int index = stackPane.getChildren().indexOf(node);
        if (index >= 0) {
            stackPane.getChildren().add(index + 1, value);
        }
    }

    protected void pushNotification(Node node) {
        if (notifications == null) {
            notifications = new AnchorPane();
            notificationsVBox = new VBox();
            notificationsVBox.setAlignment(Pos.BOTTOM_RIGHT);
            notifications.setPickOnBounds(false);
            notificationsVBox.setPickOnBounds(false);
            notifications.getChildren().add(notificationsVBox);
            AnchorPane.setRightAnchor(notificationsVBox, 10.0);
            AnchorPane.setTopAnchor(notificationsVBox, 10.0);
            AnchorPane.setBottomAnchor(notificationsVBox, 10.0);
            notificationsVBox.setSpacing(10.0);
            push(notifications);
        }
        notificationsVBox.getChildren().add(node);
    }

    protected void pullNotification(Node node) {
        if (notifications != null) {
            notificationsVBox.getChildren().remove(node);
        }
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public final boolean isNullScene() {
        return visualComponent == null;
    }

    public Stage getStage() {
        return stage;
    }

    public void show() {
        stage.show();
    }

    public void disable() {
        if (disableCounter.incrementAndGet() != 1) return;
        Pane layout = (Pane) stackPane.getChildren().get(0);
        layout.setEffect(new GaussianBlur(20));
        if (disablePane == null) {
            disablePane = new Pane();
            disablePane.setPrefHeight(layout.getPrefHeight());
            disablePane.setPrefWidth(layout.getPrefWidth());
            addAfter(layout, disablePane);
        }
        disablePane.setVisible(true);
    }

    public void enable() {
        if (disableCounter.decrementAndGet() != 0) return;
        Pane layout = (Pane) stackPane.getChildren().get(0);
        layout.setEffect(null);
        disablePane.setVisible(false);
    }
}
