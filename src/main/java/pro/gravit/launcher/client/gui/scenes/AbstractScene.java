package pro.gravit.launcher.client.gui.scenes;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.AbstractStage;
import pro.gravit.launcher.client.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.utils.helper.LogHelper;

import java.util.function.Consumer;

public abstract class AbstractScene extends AbstractVisualComponent {
    protected final LauncherConfig launcherConfig;
    protected Pane header;

    protected AbstractScene(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
        this.launcherConfig = Launcher.getConfig();
    }

    protected AbstractStage getCurrentStage() {
        return currentStage;
    }

    public void init() throws Exception {
        layout = (Pane) LookupHelper.lookupIfPossible(getFxmlRoot(), "#layout").orElse(getFxmlRoot());
        Rectangle rect = new Rectangle(layout.getPrefWidth(), layout.getPrefHeight());
        rect.setArcHeight(15);
        rect.setArcWidth(15);
        layout.setClip(rect);
        header = (Pane) LookupHelper.lookupIfPossible(layout, "#header").orElse(null);
        sceneBaseInit();
        super.init();
    }

    protected abstract void doInit() throws Exception;

    @Override
    protected void doPostInit() throws Exception {

    }


    public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
        overlay.show(currentStage, onFinished);
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request,
            Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onError);
    }

    protected final <T extends WebSocketEvent> void processRequest(String message, Request<T> request,
            Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(this, message, request, onSuccess, onException, onError);
    }

    public void disable() {
        currentStage.disable();
    }

    public void enable() {
        currentStage.enable();
    }

    public abstract void reset();

    private void sceneBaseInit() {
        if (header == null) {
            LogHelper.warning("Scene %s header button(#close, #hide) deprecated", getName());
            LookupHelper.<ButtonBase>lookupIfPossible(layout, "#close")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(layout, "#hide")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
        } else {
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#exit")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#minimize")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#lang").ifPresent((b) -> {

                b.setContextMenu(makeLangContextMenu());
                b.setOnMousePressed((e) -> {
                    if (!e.isPrimaryButtonDown()) return;
                    b.getContextMenu().show(b, e.getScreenX(), e.getScreenY());
                });
            });
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#deauth").ifPresent(b -> b.setOnAction(
                    (e) -> application.messageManager.showApplyDialog(
                            application.getTranslation("runtime.scenes.settings.exitDialog.header"),
                            application.getTranslation("runtime.scenes.settings.exitDialog.description"),
                            this::userExit, () -> {}, true)));
        }
        currentStage.enableMouseDrag(layout);
    }

    private ContextMenu makeLangContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("langChoice");
        for (RuntimeSettings.LAUNCHER_LOCALE locale : RuntimeSettings.LAUNCHER_LOCALE.values()) {
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
        processRequest(application.getTranslation("runtime.scenes.settings.exitDialog.processing"), new ExitRequest(),
                       (event) -> {
                           // Exit to main menu
                           ContextHelper.runInFxThreadStatic(() -> {
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
                       }, (event) -> {});
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
