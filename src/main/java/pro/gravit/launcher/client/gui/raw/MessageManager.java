package pro.gravit.launcher.client.gui.raw;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import pro.gravit.launcher.client.ClientLauncher;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import javax.swing.*;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageManager {
    private AtomicInteger count = new AtomicInteger(0);
    private Queue<Scene> fxmls = new ConcurrentLinkedDeque<>();
    public final JavaFXApplication application;

    public MessageManager(JavaFXApplication application) {
        this.application = application;
    }

    public void noJavaFxAlert()
    {
        if(application == null)
        {
            String message = String.format("Библиотеки JavaFX не найдены. У вас %s(x%d) ОС %s(x%d). Java %s. Установите Java с поддержкой JavaFX, например OracleJRE 8 x%d с официального сайта.\nЕсли вы не можете решить проблему самостоятельно обратитесь к администрации своего проекта" , JVMHelper.RUNTIME_MXBEAN.getVmName(),
                    JVMHelper.JVM_BITS, JVMHelper.OS_TYPE.name, JVMHelper.OS_BITS, JVMHelper.RUNTIME_MXBEAN.getSpecVersion(), JVMHelper.OS_BITS);
            JOptionPane.showMessageDialog(null, message, "GravitLauncher", JOptionPane.ERROR_MESSAGE);
        }
    }
    public void createNotification(String head, String message)
    {
        Scene scene = fxmls.poll();
        if(scene == null)
        {
            try {
                Future<Pane> future = application.getNoCacheFxml("components/notification.fxml");
                Pane pane = future.get();
                scene = new Scene(pane);
            } catch (IOException | InterruptedException | ExecutionException e) {
                LogHelper.error(e);
                return;
            }
        }
        Parent finalPane = scene.getRoot();
        Scene finalScene = scene;
        ContextHelper.runInFxThreadStatic(() -> {
            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            ((Text) finalPane.lookup("#notificationHeading")).setText(head);
            ((Text) finalPane.lookup("#notificationText")).setText(message);
            Stage notificationStage = application.newStage();
            Runnable onClose = () -> {
                notificationStage.hide();
                count.getAndDecrement();
                fxmls.add(finalScene);
            };
            finalPane.setOnMouseClicked((e) -> onClose.run());
            notificationStage.setAlwaysOnTop(true);
            notificationStage.setScene(finalScene);
            notificationStage.sizeToScene();
            notificationStage.show();
            int cnt =count.getAndIncrement() + 1;
            double maxX = bounds.getMaxX();
            double maxY = bounds.getMaxY();
            double x = maxX-notificationStage.getWidth()*1.1;
            double y = maxY-notificationStage.getHeight()*cnt*1.1;
            LogHelper.dev("Screen %f %f setted %f %f", maxX, maxY, x , y);
            notificationStage.setX(x);
            notificationStage.setY(y);
            AbstractScene.fade(finalPane, 2500, 1.0, 0.0, (e) -> {
                onClose.run();
            });
        });
    }
}
