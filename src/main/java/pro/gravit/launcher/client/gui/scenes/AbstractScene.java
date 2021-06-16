package pro.gravit.launcher.client.gui.scenes;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.JavaRuntimeModule;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.impl.AbstractStage;
import pro.gravit.launcher.client.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.utils.helper.LogHelper;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.PropertyResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractScene extends AbstractVisualComponent {
    protected final LauncherConfig launcherConfig;
    protected Scene scene;
    protected Pane header;
    protected Pane disablePane;
    private Node currentOverlayNode;
    private AbstractOverlay currentOverlay;
    private AtomicInteger enabled = new AtomicInteger(0);
    private boolean hideTransformStarted = false;

    protected AbstractScene(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
        this.launcherConfig = Launcher.getConfig();
    }

    protected AbstractStage getCurrentStage() {
        return currentStage;
    }

    public void init() throws Exception {
        if (scene == null) {
            scene = new Scene(getFxmlRoot());
            scene.setFill(Color.TRANSPARENT);
        }
        layout = (Pane) LookupHelper.lookupIfPossible(scene.getRoot(), "#layout").orElse(scene.getRoot());
        Rectangle rect = new Rectangle(layout.getPrefWidth(),layout.getPrefHeight());
        rect.setArcHeight(15);
        rect.setArcWidth(15);
        layout.setClip(rect);
        header = (Pane) LookupHelper.lookupIfPossible(layout, "#header").orElse(null);
        sceneBaseInit();
        super.init();
    }

    protected abstract void doInit() throws Exception;

    public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
        currentOverlay = overlay;
        currentOverlay.show(currentStage);
        showOverlay(overlay.getLayout(), onFinished);
    }

    private void showOverlay(Pane newOverlay, EventHandler<ActionEvent> onFinished) {
        if (newOverlay == null) throw new NullPointerException();
        if (currentOverlayNode != null) {
            swapOverlay(newOverlay, onFinished);
            return;
        }
        currentOverlayNode = newOverlay;
        Pane root = (Pane) scene.getRoot();
        disable();
        root.getChildren().add(newOverlay);
        newOverlay.setLayoutX((root.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
        newOverlay.setLayoutY((root.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);
        newOverlay.toFront();
        newOverlay.requestFocus();
        fade(newOverlay, 0.0, 0.0, 1.0, onFinished);
    }

    public void hideOverlay(double delay, EventHandler<ActionEvent> onFinished) {
        if (currentOverlayNode == null)
            return;
        if (currentOverlay == null)
            return;
        if(hideTransformStarted) {
            if(onFinished != null) {
                contextHelper.runInFxThread(() -> onFinished.handle(null));
            }
        }
        hideTransformStarted = true;
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlayNode, delay, 1.0, 0.0, (e) -> {
            root.getChildren().remove(currentOverlayNode);
            root.requestFocus();
            enable();
            currentOverlayNode = null;
            if (currentOverlay != null) currentOverlay.reset();
            currentOverlay = null;
            if (onFinished != null) {
                onFinished.handle(e);
            }
            hideTransformStarted = false;
        });
    }

    private void swapOverlay(Pane newOverlay, EventHandler<ActionEvent> onFinished) {
        if (currentOverlayNode == null)
            throw new IllegalStateException("Try swap null overlay");
        Pane root = (Pane) scene.getRoot();
        fade(currentOverlayNode, 0, 1.0, 0.0, (e) -> {
            if (currentOverlayNode != newOverlay) {
                ObservableList<Node> child = root.getChildren();
                child.set(child.indexOf(currentOverlayNode), newOverlay);
            }
            newOverlay.setLayoutX((root.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
            newOverlay.setLayoutY((root.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);
            currentOverlayNode = newOverlay;
            newOverlay.toFront();
            newOverlay.requestFocus();
            fade(newOverlay, 0.0, 0.0, 1.0, onFinished);
        });
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onError);
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request, Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onException, onError);
    }

    public AbstractOverlay getCurrentOverlay() {
        return currentOverlay;
    }

    public void disable() {
        LogHelper.debug("Scene %s disabled (%d)", getName(), enabled.incrementAndGet());
        if(enabled.get() != 1) return;
        Pane root = (Pane) scene.getRoot();
        if(layout == root) {
            throw new IllegalStateException("AbstractScene.disable() failed: layout == root");
        }
        layout.setEffect(new GaussianBlur(20));
        if(disablePane == null) {
            disablePane = new Pane();
            disablePane.setPrefHeight(root.getPrefHeight());
            disablePane.setPrefWidth(root.getPrefWidth());
            int index = root.getChildren().indexOf(layout);
            root.getChildren().add(index+1, disablePane);
            disablePane.setVisible(true);
        }
    }

    public void enable() {
        LogHelper.debug("Scene %s enabled (%d)", getName(), enabled.decrementAndGet());
        if(enabled.get() != 0) return;
        layout.setEffect(new GaussianBlur(0));
        disablePane.setVisible(false);
    }

    public boolean isEnabled() {
        return enabled.get() == 0;
    }

    public abstract void reset();

    public Scene getScene() {
        return scene;
    }

    private void sceneBaseInit() {
        if(header == null) {
            LogHelper.warning("Scene %s header button(#close, #hide) deprecated", getName());
            LookupHelper.<ButtonBase>lookupIfPossible(layout,  "#close").ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(layout,  "#hide").ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
        } else {
            LookupHelper.<ButtonBase>lookupIfPossible(header,  "#controls", "#exit").ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(header,  "#controls", "#minimize").ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#lang").ifPresent((b) -> {

                b.setContextMenu(makeLangContextMenu());
                b.setOnMousePressed((e) -> {
                    if (!e.isPrimaryButtonDown())
                        return;
                    b.getContextMenu().show(b, e.getScreenX(), e.getScreenY());
                });
            });
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#deauth").ifPresent(b -> b.setOnAction((e) ->
                    application.messageManager.showApplyDialog(application.getTranslation("runtime.scenes.settings.exitDialog.header"),
                            application.getTranslation("runtime.scenes.settings.exitDialog.description"), this::userExit
                                    , () -> {
                            }, true)));
        }
        currentStage.enableMouseDrag(layout);
    }

    private ContextMenu makeLangContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("langChoice");
        for(RuntimeSettings.LAUNCHER_LOCALE locale : RuntimeSettings.LAUNCHER_LOCALE.values()) {
            MenuItem item = new MenuItem(locale.displayName);
            item.setOnAction(e -> {
                try {
                    application.updateLocaleResources(locale.name);
                    application.runtimeSettings.locale = locale;
                    application.gui.reload();
                } catch (Exception exception) {
                    errorHandle(exception);
                }
            });
            contextMenu.getItems().add(item);
        }
        return contextMenu;
    }

    protected void userExit() {
        processRequest(application.getTranslation("runtime.scenes.settings.exitDialog.processing"),
                new ExitRequest(), (event) -> {
                    // Exit to main menu
                    ContextHelper.runInFxThreadStatic(() -> {
                        hideOverlay(0, null);
                        application.gui.loginScene.clearPassword();
                        application.gui.loginScene.reset();
                        try {
                            application.saveSettings();
                            application.stateService.exit();
                            switchScene(application.gui.loginScene);
                        } catch (Exception ex) {
                            errorHandle(ex);
                        }
                    });
                }, (event) -> {

                });
    }

    protected void switchScene(AbstractScene scene) throws Exception {
        currentStage.setScene(scene);
    }

    public Node getHeader() {
        return header;
    }

    public static void runLater(double delay, EventHandler<ActionEvent> callback) {
        fade(null, delay, 0.0, 1.0, callback);
    }
}
