package pro.gravit.launcher.client.gui.stage;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.dialogs.AbstractDialog;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.AbstractStage;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;

public class DialogStage extends AbstractStage {
    public DialogStage(JavaFXApplication application, String title, AbstractDialog dialog) throws Exception {
        super(application.newStage());
        stage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> {
            e.consume();
        });
        // Icons
        try {
            Image icon = new Image(JavaFXApplication.getResourceURL("favicon.png").toString());
            stage.getIcons().add(icon);
        } catch (IOException e) {
            LogHelper.error(e);
        }
        setScene(dialog);
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        if(bounds.getMaxX() == 0 || bounds.getMaxY() == 0) {
            bounds = screen.getBounds();
        }
        LogHelper.info("Bounds: X: %f Y: %f", bounds.getMaxX(), bounds.getMaxY());
        LookupHelper.Point2D coords = dialog.getOutSceneCoords(bounds);
        stage.setX(coords.x);
        stage.setY(coords.y);
    }
}
