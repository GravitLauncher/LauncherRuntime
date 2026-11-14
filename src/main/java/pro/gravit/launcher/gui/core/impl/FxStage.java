package pro.gravit.launcher.gui.core.impl;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.utils.JavaFxUtils;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class FxStage {

    private static final Logger logger =
            LoggerFactory.getLogger(FxStage.class);

    protected final JavaFXApplication application;
    protected final Stage stage;
    protected final Scene scene;
    protected final StackPane stackPane;
    protected final StackPane contentContainer;
    protected final Pane overlayContainer;
    protected final Pane backgroundContainer;
    protected UIComponent visualComponent;
    protected UIComponent background;
    protected Pane disablePane;
    protected VBox notificationsVBox;
    protected AnchorPane notifications;
    private final AtomicInteger disableCounter = new AtomicInteger(0);
    protected List<String> sceneFlow = new LinkedList<>();

    protected FxStage(JavaFXApplication application, Stage stage) {
        this.application = application;
        this.stage = stage;
        this.stackPane = new StackPane();
        this.backgroundContainer = new StackPane();
        this.contentContainer = new StackPane();
        this.overlayContainer = new StackPane();
        backgroundContainer.setMouseTransparent(true);
        overlayContainer.setPickOnBounds(false);
        stackPane.getChildren().addAll(backgroundContainer, contentContainer, overlayContainer);
        this.scene = new Scene(stackPane);
        this.stage.setScene(scene);
        resetStyles();
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

    public void resetStyles() {
        try {
            this.scene.getStylesheets().clear();
            this.scene.getStylesheets().add(JavaFxUtils.getStyleUrl("styles/variables").toString());
            this.scene.getStylesheets().add(JavaFxUtils.getStyleUrl("styles/global").toString());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load window styles", e);
        }
    }

    public void enableMouseDrag(Node node) {
        AtomicReference<Point2D> movePoint = new AtomicReference<>();
        if (node == null) return;
        node.setOnMousePressed(event -> {
            Node target = event.getTarget() instanceof Node value ? value : null;
            while (target != null && target != node) {
                if (target instanceof javafx.scene.control.Control) { movePoint.set(null); return; }
                target = target.getParent();
            }
            movePoint.set(new Point2D(event.getSceneX(), event.getSceneY()));
        });
        node.setOnMouseDragged(event -> {
            if (movePoint.get() == null) {
                return;
            }
            stage.setX(event.getScreenX() - movePoint.get().getX());
            stage.setY(event.getScreenY() - movePoint.get().getY());
        });
    }

    public UIComponent getVisualComponent() {
        return visualComponent;
    }

    public void setScene(FxComponent visualComponent, boolean addToFlow) throws Exception {
        pro.gravit.launcher.gui.core.ui.FxDispatcher.checkThread();
        long startNanos = System.nanoTime();
        String oldSceneName = this.visualComponent == null ? "null" : this.visualComponent.getName();
        if (visualComponent == null) {
            if (this.visualComponent != null) this.visualComponent.onHide();
            this.visualComponent = null;
            contentContainer.getChildren().clear();
            return;
        }
        UIComponent oldComponent = this.visualComponent;
        if (oldComponent == visualComponent) {
            if (visualComponent.isResetOnShow) visualComponent.reset();
            return;
        }
        visualComponent.currentStage = this;
        long initStartedAt = System.nanoTime();
        if (!visualComponent.isInit()) {
            visualComponent.init();
        }
        long initFinishedAt = System.nanoTime();
        long resetStartedAt = initFinishedAt;
        if (visualComponent.isResetOnShow) {
            visualComponent.reset();
        }
        long resetFinishedAt = System.nanoTime();
        contentContainer.getChildren().setAll(visualComponent.getFxmlRoot());
        this.visualComponent = visualComponent;
        long mountedAt;
        try {
            if (!stage.isShowing()) stage.sizeToScene();
            mountedAt = System.nanoTime();
            visualComponent.postInit();
        } catch (Exception | Error failure) {
            this.visualComponent = oldComponent;
            contentContainer.getChildren().clear();
            if (oldComponent != null) contentContainer.getChildren().setAll(oldComponent.getLayout());
            throw failure;
        }
        long postInitFinishedAt = System.nanoTime();
        if (oldComponent != null) oldComponent.onHide();
        this.visualComponent = visualComponent;
        visualComponent.onShow();
        if ("login".equals(visualComponent.getName())) sceneFlow.clear();
        if (addToFlow && !visualComponent.isDisableReturnBack()) sceneFlow.add(visualComponent.getName());
        logger.info("Scene set oldScene={} newScene={} addToFlow={} initMs={} resetMs={} mountMs={} postInitMs={} totalMs={} thread={}",
                oldSceneName, visualComponent.getName(), addToFlow,
                formatMs(initFinishedAt - initStartedAt),
                formatMs(resetFinishedAt - resetStartedAt),
                formatMs(mountedAt - resetFinishedAt),
                formatMs(postInitFinishedAt - mountedAt),
                formatMs(postInitFinishedAt - startNanos),
                Thread.currentThread().getName());
        application.logUiActionPhase("scene-mounted",
                "oldScene=%s newScene=%s resetOnShow=%s".formatted(oldSceneName, visualComponent.getName(),
                        visualComponent.isResetOnShow));
        Platform.runLater(() -> application.finishUiActionForScene(visualComponent.getName(),
                "stageWidth=%.1f stageHeight=%.1f".formatted(stage.getWidth(), stage.getHeight())));
    }

    public UIComponent back() throws Exception {
        int index = sceneFlow.size() - 1;
        if (visualComponent != null && index >= 0 && sceneFlow.get(index).equals(visualComponent.getName())) index--;
        while (index >= 0) {
            FxComponent component = application.gui.getByName(sceneFlow.get(index));
            if (component != null && !component.isDisableReturnBack()) {
                setScene(component, false);
                sceneFlow.subList(index + 1, sceneFlow.size()).clear();
                return component;
            }
            index--;
        }
        return null;
    }

    public Scene getScene() { return scene; }

    public void push(Node node) {
        for (Node other : overlayContainer.getChildren()) {
            if (other != disablePane && other != notifications) other.setDisable(true);
        }
        overlayContainer.getChildren().add(node);
    }

    public boolean contains(Node node) {
        return stackPane.getChildren().contains(node) || contentContainer.getChildren().contains(node) || overlayContainer.getChildren().contains(node) || backgroundContainer.getChildren().contains(node);
    }

    public void pull(Node node) {
        overlayContainer.getChildren().remove(node);
        backgroundContainer.getChildren().remove(node);
        for (int i = overlayContainer.getChildren().size() - 1; i >= 0; i--) {
            Node other = overlayContainer.getChildren().get(i);
            if (other != disablePane && other != notifications) { other.setDisable(false); break; }
        }
    }

    public void addAfter(Node node, Node value) {
        if (node == contentContainer) overlayContainer.getChildren().add(value);
    }

    public void addBefore(Node node, Node value) {
        if (node == contentContainer) backgroundContainer.getChildren().add(value);
    }

    public int getScenePosition() {
        return 0;
    }

    protected void pushNotification(Node node) {
        if (notifications == null) {
            notifications = new AnchorPane();
            notificationsVBox = new VBox();
            notificationsVBox.setAlignment(Pos.BOTTOM_RIGHT);
            notifications.setPickOnBounds(false);
            notifications.setDisable(disableCounter.get() > 0);
            notificationsVBox.setPickOnBounds(false);
            notifications.getChildren().add(notificationsVBox);
            AnchorPane.setRightAnchor(notificationsVBox, 10.0);
            AnchorPane.setTopAnchor(notificationsVBox, 10.0);
            AnchorPane.setBottomAnchor(notificationsVBox, 10.0);
            notificationsVBox.setSpacing(10.0);
            overlayContainer.getChildren().add(notifications);
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
        logger.debug("Disable scene: stack_num: {} | blur: {} | counter: {}", stackPane.getChildren().size(), disablePane == null ? "null" : "not null", value);
        if (value != 1) return;
        if (visualComponent == null) { disableCounter.set(0); return; }
        Pane layout = visualComponent.getLayout();
        contentContainer.setEffect(new GaussianBlur(16));
        if (disablePane == null) {
            disablePane = new Pane();
            disablePane.setMouseTransparent(false);
            disablePane.prefWidthProperty().bind(stackPane.widthProperty());
            disablePane.prefHeightProperty().bind(stackPane.heightProperty());
            overlayContainer.getChildren().add(0, disablePane);
        }
        contentContainer.setDisable(true);
        if (notifications != null) notifications.setDisable(true);
        disablePane.setVisible(true);
    }

    public AutoCloseable blockInput() {
        disable();
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) enable();
        };
    }

    public void enable() {
        var value = disableCounter.updateAndGet(current -> Math.max(0, current - 1));
        logger.debug("Enable scene: stack_num: {} | blur: {} | counter: {}", stackPane.getChildren().size(), disablePane == null ? "null" : "not null", value);
        if (value != 0) return;
        contentContainer.setEffect(null);
        contentContainer.setDisable(false);
        if (notifications != null) notifications.setDisable(false);
        if (disablePane != null) disablePane.setVisible(false);
    }

    private static String formatMs(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }
}
