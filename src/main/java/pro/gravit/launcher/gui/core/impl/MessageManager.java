package pro.gravit.launcher.gui.core.impl;

import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.dialogs.AbstractDialog;
import pro.gravit.launcher.gui.dialogs.ApplyDialog;
import pro.gravit.launcher.gui.dialogs.InfoDialog;
import pro.gravit.launcher.gui.dialogs.NotificationDialog;
import pro.gravit.launcher.gui.helper.PositionHelper;
import pro.gravit.launcher.gui.stage.DialogStage;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicReference;

public class MessageManager {
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void registerPublicApi() {
        pro.gravit.launcher.client.api.DialogService.setDialogImpl(new pro.gravit.launcher.client.api.DialogService.DialogServiceImplementation() {
            public void showDialog(String header, String text, Runnable accept, Runnable close) { MessageManager.this.showDialog(header, text, accept, close, true); }
            public void showApplyDialog(String header, String text, Runnable accept, Runnable deny) { MessageManager.this.showApplyDialog(header, text, accept, deny, true); }
            public void showApplyDialog(String header, String text, Runnable accept, Runnable deny, Runnable close) { MessageManager.this.showApplyDialog(header, text, accept, deny, close, true); }
            public void showTextDialog(String header, java.util.function.Consumer<String> accept, Runnable close) {
                ContextHelper.runInFxThreadStatic(() -> {
                    javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
                    dialog.setTitle(header); dialog.setHeaderText(header);
                    dialog.initOwner(application.getMainStage().getStage());
                    dialog.setOnHidden(event -> {
                        if (dialog.getResult() == null) close.run(); else accept.accept(dialog.getResult());
                    });
                    dialog.show();
                });
            }
        });
        pro.gravit.launcher.client.api.DialogService.setNotificationImpl((type, header, message) -> createNotification(header, message));
    }

    public void createNotification(String head, String message) {
        ContextHelper.runInFxThreadStatic(() -> createNotification(head, message, application.getCurrentScene() != null));
    }

    public void initDialogInScene(FxScene scene, AbstractDialog dialog) {
        showAbstractDialog(dialog, dialog.getName(), true);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            ContextHelper.runInFxThreadStatic(() -> createNotification(head, message, isLauncher));
            return;
        }
        NotificationDialog dialog = new NotificationDialog(application, head, message);
        if (isLauncher) {
            FxStage stage = application.getMainStage();
            if (stage == null)
                throw new NullPointerException("Try show launcher notification in application.getMainStage() == null");
            ContextHelper.runInFxThreadStatic(() -> {
                dialog.init();
                stage.pushNotification(dialog.getFxmlRootPrivate());
                dialog.setOnClose(() -> { stage.pullNotification(dialog.getFxmlRootPrivate()); dialog.dispose(); });
                ContextHelper.runAfterTimeoutStatic(Duration.seconds(3), () -> {
                    dialog.close();
                    return null;
                });
            });
        } else {
            AtomicReference<DialogStage> stage = new AtomicReference<>(null);
            ContextHelper.runInFxThreadStatic(() -> {
                dialog.getFxmlRootPrivate().applyCss();
                double contentHeight = dialog.getFxmlRootPrivate().prefHeight(360.0);
                NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot(
                        (scrollTo) -> stage.get().stage.setY(stage.get().stage.getY() + scrollTo),
                        contentHeight + 20);
                dialog.setPosition(PositionHelper.PositionInfo.BOTTOM_RIGHT, slot);
                dialog.setOnClose(() -> {
                    stage.get().close();
                    stage.get().stage.setScene(null);
                    dialog.dispose();
                });
                stage.set(new DialogStage(application, head, dialog));
                stage.get().stage.sizeToScene();
                stage.get().show();

                ContextHelper.runAfterTimeoutStatic(Duration.seconds(3), () -> {
                    dialog.close();
                    return null;
                });
            });
        }
    }

    public void showDialog(String header, String text, Runnable onApplyCallback, Runnable onCloseCallback,
            boolean isLauncher) {
        InfoDialog dialog = new InfoDialog(application, header, text, onApplyCallback, onCloseCallback);
        showAbstractDialog(dialog, header, isLauncher);
    }

    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback,
            boolean isLauncher) {
        showApplyDialog(header, text, onApplyCallback, onDenyCallback, onDenyCallback, isLauncher);
    }

    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback,
            Runnable onCloseCallback, boolean isLauncher) {
        ApplyDialog dialog = new ApplyDialog(application, header, text, onApplyCallback, onDenyCallback,
                                             onCloseCallback);
        showAbstractDialog(dialog, header, isLauncher);
    }

    public void showAbstractDialog(AbstractDialog content, String header, boolean isLauncher) {
        ContextHelper.runInFxThreadStatic(() -> {
            content.init();
            javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
            dialog.setTitle(header);
            if (application.getMainStage() != null) dialog.getDialogPane().getStylesheets().setAll(application.getMainStage().getScene().getStylesheets());
            if (application.getMainStage() != null) dialog.initOwner(application.getMainStage().getStage());
            dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialog.getDialogPane().setContent(content.getLayout());
            dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
            var closeButton = dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CLOSE);
            closeButton.setVisible(false); closeButton.setManaged(false);
            content.setOnClose(dialog::close);
            dialog.setOnHidden(event -> {
                try { content.dismiss(); } catch (Throwable error) { content.errorHandle(error); }
                finally { content.dispose(); }
            });
            dialog.show();
        });
    }
}
