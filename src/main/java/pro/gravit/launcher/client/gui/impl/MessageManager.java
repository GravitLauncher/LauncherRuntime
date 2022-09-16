package pro.gravit.launcher.client.gui.impl;

import animatefx.animation.FadeIn;
import animatefx.animation.SlideInLeft;
import animatefx.animation.SlideInRight;
import animatefx.animation.SlideOutRight;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.dialogs.*;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.helper.PositionHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.stage.DialogStage;
import pro.gravit.utils.helper.LogHelper;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MessageManager {
    public final JavaFXApplication application;
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger localCount = new AtomicInteger(0);

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void createNotification(String head, String message) {
        createNotification(head, message, application.getCurrentScene() != null);
    }

    public void initDialogInScene(AbstractScene scene, AbstractDialog dialog) {
        Pane root = (Pane) scene.getFxmlRoot();
        if (!dialog.isInit()) {
            try {
                dialog.currentStage = scene.currentStage;
                dialog.init();
            } catch (Exception e) {
                scene.errorHandle(e);
            }
        }
        Pane dialogRoot = (Pane) dialog.getFxmlRoot();
        dialog.setOnClose(() -> {
            root.getChildren().remove(dialogRoot);
            if (!(dialog instanceof NotificationDialog)) {
                scene.enable();
            }
        });
        if (dialog instanceof NotificationDialog) {

            NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot((scrollTo) -> {
                dialogRoot.setLayoutY(dialogRoot.getLayoutY() + scrollTo);
            }, ((Pane) dialog.getFxmlRoot()).getPrefHeight() + 20);
            NotificationDialog notificationDialog = (NotificationDialog) dialog;
            new SlideInRight(notificationDialog.layout).setSpeed(1.5).play();
            notificationDialog.layout.setLayoutX(700);
            notificationDialog.layout.setLayoutY(60);
            notificationDialog.setPosition(PositionHelper.PositionInfo.BOTTOM_RIGHT, slot);
        } else {
            scene.disable();
        }
        if (dialog instanceof ApplyDialog) {

            NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot((scrollTo) -> {
                dialogRoot.setLayoutY(dialogRoot.getLayoutY() + scrollTo);
            }, ((Pane) dialog.getFxmlRoot()).getPrefHeight() + 20);
            ApplyDialog applyDialog = (ApplyDialog) dialog;
            new FadeIn(applyDialog.layout).play();
            applyDialog.layout.setLayoutX(306);
            applyDialog.layout.setLayoutY(189);
        }
        LookupHelper.Point2D coords = dialog.getSceneCoords(root);
//        dialogRoot.setLayoutX(coords.x);
//        dialogRoot.setLayoutY(coords.y);
//        dialogRoot.setLayoutX(250);
//        dialogRoot.setLayoutY(150);
        LogHelper.info("X: %f Y: %f", coords.x, coords.y);
        root.getChildren().add(dialogRoot);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        NotificationDialog dialog = new NotificationDialog(application, head, message);
        if (isLauncher) {
            AbstractScene scene = application.getCurrentScene();
            if (scene == null) {
                throw new NullPointerException("Try show launcher notification in application.getCurrentScene() == null");
            }
            ContextHelper.runInFxThreadStatic(() -> {
                initDialogInScene(scene, dialog);
            });
        } else {
            AtomicReference<DialogStage> stage = new AtomicReference<>(null);
            ContextHelper.runInFxThreadStatic(() -> {
                NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot((scrollTo) -> {
                    stage.get().stage.setY(stage.get().stage.getY() + scrollTo);
                }, ((Pane) dialog.getFxmlRoot()).getPrefHeight() + 20);
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

    public void showDialog(String header, String text, Runnable onApplyCallback, Runnable onCloseCallback, boolean isLauncher) {
        InfoDialog dialog = new InfoDialog(application, header, text, onApplyCallback, onCloseCallback);
        showAbstractDialog(dialog, header, isLauncher);
    }

    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback, boolean isLauncher) {
        showApplyDialog(header, text, onApplyCallback, onDenyCallback, onDenyCallback, isLauncher);
    }

    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback, Runnable onCloseCallback, boolean isLauncher) {
        ApplyDialog dialog = new ApplyDialog(application, header, text, onApplyCallback, onDenyCallback, onCloseCallback);
        showAbstractDialog(dialog, header, isLauncher);
    }

    public void showTextDialog(String header, Consumer<String> onApplyCallback, Runnable onCloseCallback, boolean isLauncher) {
        TextDialog dialog = new TextDialog(application, header, "", onApplyCallback, onCloseCallback);
        showAbstractDialog(dialog, header, isLauncher);
    }

    public void showAbstractDialog(AbstractDialog dialog, String header, boolean isLauncher) {
        if (isLauncher) {
            AbstractScene scene = application.getCurrentScene();
            if (scene == null) {
                throw new NullPointerException("Try show launcher dialog in application.getCurrentScene() == null");
            }
            ContextHelper.runInFxThreadStatic(() -> {
                initDialogInScene(scene, dialog);
            });
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
