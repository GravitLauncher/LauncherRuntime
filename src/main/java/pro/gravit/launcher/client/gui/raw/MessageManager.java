package pro.gravit.launcher.client.gui.raw;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MessageManager {
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger localCount = new AtomicInteger(0);
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void createNotification(String head, String message) {
        createNotification(head, message, application.getCurrentScene() != null);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        if (isLauncher && application.getCurrentScene() == null)
            throw new NullPointerException("Try show launcher notification in application.getCurrentScene() == null");
        Pane pane;
        try {
            pane = (Pane) application.getNonCachedFxmlAsync("components/notification.fxml").get();
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        Pane finalPane = pane;
        Scene scene = isLauncher ? null : new Scene(finalPane);
        ContextHelper.runInFxThreadStatic(() -> {
            LookupHelper.<Text>lookup(finalPane, "#notificationHeading").setText(head);
            LookupHelper.<Text>lookup(finalPane, "#notificationText").setText(message);
            Runnable onClose;
            if (!isLauncher) {
                Screen screen = Screen.getPrimary();
                Rectangle2D bounds = screen.getVisualBounds();
                Stage notificationStage = application.newStage(StageStyle.TRANSPARENT);
                onClose = () -> {
                    notificationStage.hide();
                    notificationStage.setScene(null);
                    count.getAndDecrement();
                };
                finalPane.setOnMouseClicked((e) -> onClose.run());
                notificationStage.setAlwaysOnTop(true);
                notificationStage.setScene(scene);
                notificationStage.sizeToScene();
                notificationStage.setResizable(false);
                notificationStage.setTitle(head);
                notificationStage.show();
                int currentCount = count.getAndIncrement() + 1;
                double maxX = bounds.getMaxX();
                double maxY = bounds.getMaxY();
                double x = maxX - notificationStage.getWidth() * 1.1;
                double y = maxY - notificationStage.getHeight() * currentCount * 1.1;
                LogHelper.dev("Screen %f %f setted %f %f", maxX, maxY, x, y);
                notificationStage.setX(x);
                notificationStage.setY(y);
            } else {
                AbstractScene currentScene = application.getCurrentScene();
                Pane root = (Pane) currentScene.getScene().getRoot();
                root.getChildren().add(finalPane);
                onClose = () -> {
                    root.getChildren().remove(finalPane);
                    localCount.getAndDecrement();
                };
                int currentCount = localCount.getAndIncrement();
                double maxX = root.getWidth();
                double maxY = root.getHeight();
                finalPane.setVisible(true);
                double x = maxX - finalPane.getPrefWidth();
                double y = finalPane.getPrefHeight() * currentCount * 1.1 + finalPane.getPrefHeight() * 0.6;
                finalPane.setLayoutX(x);
                finalPane.setLayoutY(y);
                LogHelper.dev("Layout %f %f setted %f %f", maxX, maxY, x, y);
            }
            finalPane.setOnMouseClicked((e) -> {
                if (!e.getButton().equals(MouseButton.PRIMARY))
                    return;
                onClose.run();
            });
            AbstractScene.fade(finalPane, 2500, 1.0, 0.0, (e) -> onClose.run());
        });
    }

    public void showDialog(String header, String text, Runnable onApplyCallback, Runnable onCloseCallback, boolean isLauncher) {
        showAbstractDialog("components/dialog.fxml", header, (pane) -> {
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
        showAbstractDialog("components/dialogApply.fxml", header, (pane) -> {
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
            LookupHelper.<Button>lookup(pane, "#deny").setOnAction((e) -> {
                onClose.run();
                onDenyCallback.run();
            });
        }, isLauncher);
    }

    public void showTextDialog(String header, Consumer<String> onApplyCallback, Runnable onCloseCallback, boolean isLauncher) {
        showAbstractDialog("components/dialogTextInput.fxml", header, (pane) ->
                        LookupHelper.<Text>lookup(pane, "#headingDialog").setText(header),
                (pane, onClose) -> {
                    LookupHelper.<Button>lookup(pane, "#close").setOnAction((e) -> {
                        onClose.run();
                        onCloseCallback.run();
                    });
                    TextField a = LookupHelper.<TextField>lookup(pane, "#dialogInput");
                    LookupHelper.<Button>lookup(pane, "#apply").setOnAction((e) -> {
                        onClose.run();
                        onApplyCallback.accept(a.getText());
                    });
                }, isLauncher);
    }

    private void showAbstractDialog(String componentName, String nativeHeader, Consumer<Pane> initPane, BiConsumer<Pane, Runnable> bindPane, boolean isLauncher) {
        Pane pane;
        try {
            pane = (Pane) application.getNonCachedFxmlAsync(componentName).get();
        } catch (IOException | InterruptedException | ExecutionException e) {
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
                root.getChildren().add(finalPane);
                onClose = () -> root.getChildren().remove(finalPane);
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
