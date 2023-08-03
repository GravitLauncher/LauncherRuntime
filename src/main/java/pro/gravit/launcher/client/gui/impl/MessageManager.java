package pro.gravit.launcher.client.gui.impl;

import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.dialogs.*;
import pro.gravit.launcher.client.gui.helper.PositionHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.stage.DialogStage;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MessageManager {
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void createNotification(String head, String message) {
        createNotification(head, message, application.getCurrentScene() != null);
    }

    public void initDialogInScene(AbstractScene scene, AbstractDialog dialog) {
        Pane dialogRoot = (Pane) dialog.getFxmlRoot();
        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        HBox hBox = new HBox();
        hBox.getChildren().add(dialogRoot);
        vbox.getChildren().add(hBox);
        hBox.setAlignment(Pos.CENTER);
        if (!dialog.isInit()) {
            try {
                dialog.currentStage = scene.currentStage;
                dialog.init();
            } catch (Exception e) {
                scene.errorHandle(e);
            }
        }
        dialog.setOnClose(() -> {
            scene.currentStage.pull(vbox);
            vbox.getChildren().clear();
            hBox.getChildren().clear();
            scene.currentStage.enable();
        });
        scene.disable();
        scene.currentStage.push(vbox);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        NotificationDialog dialog = new NotificationDialog(application, head, message);
        if (isLauncher) {
            AbstractStage stage = application.getMainStage();
            if (stage == null)
                throw new NullPointerException("Try show launcher notification in application.getMainStage() == null");
            ContextHelper.runInFxThreadStatic(() -> {
                dialog.init();
                stage.pushNotification(dialog.getFxmlRoot());
                dialog.setOnClose(() -> stage.pullNotification(dialog.getFxmlRoot()));
            });
        } else {
            AtomicReference<DialogStage> stage = new AtomicReference<>(null);
            ContextHelper.runInFxThreadStatic(() -> {
                NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot(
                        (scrollTo) -> stage.get().stage.setY(stage.get().stage.getY() + scrollTo),
                        ((Pane) dialog.getFxmlRoot()).getPrefHeight() + 20);
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

    public void showTextDialog(String header, Consumer<String> onApplyCallback, Runnable onCloseCallback,
            boolean isLauncher) {
        TextDialog dialog = new TextDialog(application, header, "", onApplyCallback, onCloseCallback);
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
