package pro.gravit.launcher.gui.impl;

import javafx.scene.layout.Pane;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.dialogs.*;
import pro.gravit.launcher.gui.helper.PositionHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.stage.DialogStage;

import java.util.concurrent.atomic.AtomicReference;

public class MessageManager {
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void createNotification(String head, String message) {
        createNotification(head, message, application.getCurrentScene() != null);
    }

    public void initDialogInScene(AbstractScene scene, AbstractDialog dialog) {
        Pane dialogRoot = (Pane) dialog.getFxmlRootPrivate();
        if (!dialog.isInit()) {
            try {
                dialog.currentStage = scene.currentStage;
                dialog.init();
            } catch (Exception e) {
                scene.errorHandle(e);
            }
        }
        dialog.setOnClose(() -> {
            scene.currentStage.pull(dialogRoot);
            scene.currentStage.enable();
        });
        scene.disable();
        scene.currentStage.push(dialogRoot);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        NotificationDialog dialog = new NotificationDialog(application, head, message);
        if (isLauncher) {
            AbstractStage stage = application.getMainStage();
            if (stage == null)
                throw new NullPointerException("Try show launcher notification in application.getMainStage() == null");
            ContextHelper.runInFxThreadStatic(() -> {
                dialog.init();
                stage.pushNotification(dialog.getFxmlRootPrivate());
                dialog.setOnClose(() -> stage.pullNotification(dialog.getFxmlRootPrivate()));
            });
        } else {
            AtomicReference<DialogStage> stage = new AtomicReference<>(null);
            ContextHelper.runInFxThreadStatic(() -> {
                NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot(
                        (scrollTo) -> stage.get().stage.setY(stage.get().stage.getY() + scrollTo),
                        ((Pane) dialog.getFxmlRootPrivate()).getPrefHeight() + 20);
                dialog.setPosition(PositionHelper.PositionInfo.BOTTOM_RIGHT, slot);
                dialog.setOnClose(() -> {
                    stage.get().close();
                    stage.get().stage.setScene(null);
                });
                stage.set(new DialogStage(application, head, dialog));
                stage.get().show();
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

    public void showAbstractDialog(AbstractDialog dialog, String header, boolean isLauncher) {
        if (isLauncher) {
            AbstractScene scene = application.getCurrentScene();
            if (scene == null)
                throw new NullPointerException("Try show launcher dialog in application.getCurrentScene() == null");
            ContextHelper.runInFxThreadStatic(() -> initDialogInScene(scene, dialog));
        } else {
            AtomicReference<DialogStage> stage = new AtomicReference<>(null);
            ContextHelper.runInFxThreadStatic(() -> {
                stage.set(new DialogStage(application, header, dialog));
                stage.get().show();
            });
            dialog.setOnClose(() -> {
                stage.get().close();
                stage.get().stage.setScene(null);
            });
        }
    }
}
