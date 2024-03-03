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
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.DesignConstants;
import pro.gravit.utils.helper.LogHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
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
            URL globalCss;
            try {
                globalCss = JavaFXApplication.getResourceURL("styles/global.bss");
            } catch (FileNotFoundException e) {
                globalCss = JavaFXApplication.getResourceURL("styles/global.css");
            }
            this.scene.getStylesheets().add(globalCss.toString());
        } catch (IOException e) {
            LogHelper.error(e);
        }
        {
        }
    }

    protected void setClipRadius(double width, double height) {
        Rectangle rect = new Rectangle(stackPane.getPrefWidth(), stackPane.getPrefHeight());
        rect.heightProperty().bind(stackPane.heightProperty());
        rect.widthProperty().bind(stackPane.widthProperty());
        rect.setArcHeight(height);
        rect.setArcWidth(width);
        stackPane.setClip(rect);
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
            if(!stackPane.getChildren().isEmpty()) {
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
        if (stackPane.getChildren().isEmpty()) {
            stackPane.getChildren().add(visualComponent.getFxmlRoot());
        } else {
            var old = stackPane.getChildren().get(0);
            if(old.getEffect() instanceof GaussianBlur blur) {
                old.setEffect(null);
                visualComponent.getFxmlRootPrivate().setEffect(blur);
            }
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
        var value = disableCounter.incrementAndGet();
        LogHelper.dev("Disable scene: stack_num: %s | blur: %s | counter: %s",stackPane.getChildren().size(), disablePane == null ? "null" : "not null", value);
        if (value != 1) return;
        Pane layout = (Pane) stackPane.getChildren().get(0);
        layout.setEffect(new GaussianBlur(150));
        if (disablePane == null) {
            disablePane = new Pane();
            disablePane.setPrefHeight(layout.getPrefHeight());
            disablePane.setPrefWidth(layout.getPrefWidth());
            addAfter(layout, disablePane);
        }
        disablePane.setVisible(true);
    }

    public void enable() {
        var value = disableCounter.decrementAndGet();
        LogHelper.dev("Enable scene: stack_num: %s | blur: %s | counter: %s",stackPane.getChildren().size(), disablePane == null ? "null" : "not null", value);
        if (value != 0) return;
        Pane layout = (Pane) stackPane.getChildren().get(0);
        layout.setEffect(null);
        disablePane.setVisible(false);
    }
}
