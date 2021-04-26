package pro.gravit.launcher.client.gui.impl;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.dialogs.AbstractDialog;
import pro.gravit.launcher.client.gui.dialogs.NotificationDialog;
import pro.gravit.launcher.client.gui.helper.PositionHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.stage.DialogStage;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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
        if(!dialog.isInit()) {
            try {
                dialog.currentStage = scene.currentStage;
                dialog.init();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Pane dialogRoot = (Pane) dialog.getFxmlRoot();
        dialog.setOnClose(() -> {
            root.getChildren().remove(dialogRoot);
        });
        if(dialog instanceof NotificationDialog) {

            NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot((scrollTo) -> {
                dialogRoot.setLayoutY(dialogRoot.getLayoutY()+scrollTo);
            }, ((Pane)dialog.getFxmlRoot()).getPrefHeight()+20);
            NotificationDialog notificationDialog = (NotificationDialog) dialog;
            notificationDialog.setPosition(PositionHelper.PositionInfo.BOTTOM_RIGHT, slot);
        }
        LookupHelper.Point2D coords = dialog.getSceneCoords(root);
        dialogRoot.setLayoutX(coords.x);
        dialogRoot.setLayoutY(coords.y);
        LogHelper.info("X: %f Y: %f", coords.x, coords.y);
        root.getChildren().add(dialogRoot);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        NotificationDialog dialog = new NotificationDialog(application, head, message);
        if(isLauncher) {
            AbstractScene scene = application.getCurrentScene();
            if(scene == null) {
                throw new NullPointerException("Try show launcher notification in application.getCurrentScene() == null");
            }
            ContextHelper.runInFxThreadStatic(() -> {
                initDialogInScene(scene, dialog);
            });
        } else {
            AtomicReference<DialogStage> stage = new AtomicReference<>(null);
            ContextHelper.runInFxThreadStatic(() -> {
                stage.set(new DialogStage(application, head, dialog));
            });
            NotificationDialog.NotificationSlot slot = new NotificationDialog.NotificationSlot((scrollTo) -> {
                stage.get().stage.setY(stage.get().stage.getY()+scrollTo);
            }, ((Pane)dialog.getFxmlRoot()).getPrefHeight()+20);
            dialog.setPosition(PositionHelper.PositionInfo.BOTTOM_RIGHT, slot);
            dialog.setOnClose(() -> {
                stage.get().close();
                stage.get().stage.setScene(null);
            });
            stage.get().show();
        }
    }

    public void showDialog(String header, String text, Runnable onApplyCallback, Runnable onCloseCallback, boolean isLauncher) {
        showAbstractDialog("dialogs/info/dialog.fxml", header, (pane) -> {
            LookupHelper.<Text>lookup(pane, "#headingDialog").setText(header);
            LookupHelper.<Text>lookup(pane, "#textDialog").setText(text);
        }, (pane, onClose) -> {
            LookupHelper.<Button>lookup(pane, "#close").setOnAction((e) -> {
                onClose.run();
                onCloseCallback.run();
            });
            LookupHelper.<Button>lookup(pane, "#apply").setOnAction((e) -> {
                onClose.run();
                onApplyCallback.run();
            });
        }, isLauncher);
    }

    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback, boolean isLauncher) {
        showApplyDialog(header, text, onApplyCallback, onDenyCallback, onDenyCallback, isLauncher);
    }

    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback, Runnable onCloseCallback, boolean isLauncher) {
        showAbstractDialog("dialogs/apply/dialog.fxml", header, (pane) -> {
            LookupHelper.<Labeled>lookup(pane, "#headingDialog").setText(header);
            LookupHelper.<Labeled>lookup(pane, "#textDialog").setText(text);
        }, (pane, onClose) -> {
            LookupHelper.<Button>lookup(pane, "#close").setOnAction((e) -> {
                onClose.run();
                onCloseCallback.run();
            });
            LookupHelper.<Button>lookup(pane, "#apply").setOnAction((e) -> {
                onClose.run();
                onApplyCallback.run();
            });
            LookupHelper.<Button>lookup(pane, "#deny").setOnAction((e) -> {
                onClose.run();
                onDenyCallback.run();
            });
        }, isLauncher);
    }

    public void showTextDialog(String header, Consumer<String> onApplyCallback, Runnable onCloseCallback, boolean isLauncher) {
        showAbstractDialog("dialogs/text/dialog.fxml", header, (pane) ->
                        LookupHelper.<Text>lookup(pane, "#headingDialog").setText(header),
                (pane, onClose) -> {
                    LookupHelper.<Button>lookup(pane, "#close").setOnAction((e) -> {
                        onClose.run();
                        onCloseCallback.run();
                    });
                    TextField a = LookupHelper.<TextField>lookup(pane, "#dialogInput");
                    EventHandler<ActionEvent> eventHandler = (e) -> {
                        onClose.run();
                        onApplyCallback.accept(a.getText());
                    };
                    LookupHelper.<Button>lookup(pane, "#apply").setOnAction(eventHandler);
                    a.setOnAction(eventHandler);
                    a.requestFocus();
                }, isLauncher);
    }

    private void showAbstractDialog(String componentName, String nativeHeader, Consumer<Pane> initPane, BiConsumer<Pane, Runnable> bindPane, boolean isLauncher) {
        Pane pane;
        try {
            pane = application.fxmlFactory.get(componentName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Pane finalPane = pane;
        Scene scene = isLauncher ? null : new Scene(finalPane);
        ContextHelper.runInFxThreadStatic(() -> {
            initPane.accept(finalPane);
            Runnable onClose;
            if (isLauncher) {
                AbstractScene currentScene = application.getCurrentScene();
                Pane root = (Pane) currentScene.getScene().getRoot();
                Pane shadow = new Pane();
                shadow.setPrefHeight(root.getPrefHeight());
                shadow.setPrefWidth(root.getPrefWidth());
                root.getChildren().add(shadow);
                root.getChildren().add(finalPane);
                onClose = () -> {
                    root.getChildren().remove(finalPane);
                    root.getChildren().remove(shadow);
                };
                pane.setLayoutX((root.getPrefWidth() - pane.getPrefWidth()) / 2.0);
                pane.setLayoutY((root.getPrefHeight() - pane.getPrefHeight()) / 2.0);
                LogHelper.debug("Layout: X: %f, Y: %f", pane.getLayoutX(), pane.getLayoutY());
            } else {
                Screen screen = Screen.getPrimary();
                Rectangle2D bounds = screen.getVisualBounds();
                Stage notificationStage = application.newStage(StageStyle.TRANSPARENT);
                onClose = () -> {
                    notificationStage.hide();
                    notificationStage.setScene(null);
                };
                finalPane.setOnMouseClicked((e) -> onClose.run());
                notificationStage.setAlwaysOnTop(true);
                notificationStage.setScene(scene);
                notificationStage.sizeToScene();
                notificationStage.setResizable(false);
                notificationStage.setTitle(nativeHeader);
                notificationStage.show();
                double x = (bounds.getMaxX() - pane.getPrefWidth()) / 2.0;
                double y = (bounds.getMaxY() - pane.getPrefHeight()) / 2.0;
                notificationStage.setX(x);
                notificationStage.setY(y);
            }
            bindPane.accept(finalPane, onClose);
        });
    }
}
