package pro.gravit.launcher.client.gui.raw;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import javax.swing.*;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageManager {
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger localCount = new AtomicInteger(0);
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void noJavaFxAlert() {
        if (application == null) {
            String message = String.format("Библиотеки JavaFX не найдены. У вас %s(x%d) ОС %s(x%d). Java %s. Установите Java с поддержкой JavaFX, например OracleJRE 8 x%d с официального сайта.\nЕсли вы не можете решить проблему самостоятельно обратитесь к администрации своего проекта", JVMHelper.RUNTIME_MXBEAN.getVmName(),
                    JVMHelper.JVM_BITS, JVMHelper.OS_TYPE.name, JVMHelper.OS_BITS, JVMHelper.RUNTIME_MXBEAN.getSpecVersion(), JVMHelper.OS_BITS);
            JOptionPane.showMessageDialog(null, message, "GravitLauncher", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void createNotification(String head, String message) {
        createNotification(head, message, application.getCurrentScene() != null);
    }

    public void createNotification(String head, String message, boolean isLauncher) {
        if (isLauncher && application.getCurrentScene() == null)
            throw new NullPointerException("Try show launcher notification in application.getCurrentScene() == null");
        Pane pane;
        try {
            pane = (Pane) application.getNoCacheFxml("components/notification.fxml").get();
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        Pane finalPane = pane;
        Scene scene = isLauncher ? null : new Scene(finalPane);
        ContextHelper.runInFxThreadStatic(() -> {
            ((Text) finalPane.lookup("#notificationHeading")).setText(head);
            ((Text) finalPane.lookup("#notificationText")).setText(message);
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
                int cnt = count.getAndIncrement() + 1;
                double maxX = bounds.getMaxX();
                double maxY = bounds.getMaxY();
                double x = maxX - notificationStage.getWidth() * 1.1;
                double y = maxY - notificationStage.getHeight() * cnt * 1.1;
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
                int cnt = localCount.getAndIncrement();
                double maxX = root.getWidth();
                double maxY = root.getHeight();
                finalPane.setVisible(true);
                double x = maxX - finalPane.getPrefWidth();
                double y = finalPane.getPrefHeight() * cnt * 1.1 + finalPane.getPrefHeight() * 0.6;
                finalPane.setLayoutX(x);
                finalPane.setLayoutY(y);
                LogHelper.dev("Layout %f %f setted %f %f", maxX, maxY, x, y);
            }
            finalPane.setOnMouseClicked((e) -> {
                if (!e.getButton().equals(MouseButton.PRIMARY)) return;
                onClose.run();
            });
            AbstractScene.fade(finalPane, 2500, 1.0, 0.0, (e) -> onClose.run());
        });
    }
}
