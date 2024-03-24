package pro.gravit.launcher.gui.stage;

import javafx.event.Event;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.StageStyle;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.DesignConstants;
import pro.gravit.launcher.gui.dialogs.AbstractDialog;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.impl.AbstractStage;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;

public class DialogStage extends AbstractStage {
    public DialogStage(JavaFXApplication application, String title, AbstractDialog dialog) throws Exception {
        super(application, application.newStage());
        stage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        stage.setOnCloseRequest(Event::consume);
        scene.setFill(Color.TRANSPARENT);
        // Icons
        try {
            Image icon = new Image(JavaFXApplication.getResourceURL("favicon.png").toString());
            stage.getIcons().add(icon);
        } catch (IOException e) {
            LogHelper.error(e);
        }
        setClipRadius(DesignConstants.SCENE_CLIP_RADIUS, DesignConstants.SCENE_CLIP_RADIUS);
        setScene(dialog, true);
        enableMouseDrag(dialog.getLayout());
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        if (bounds.getMaxX() == 0 || bounds.getMaxY() == 0) {
            bounds = screen.getBounds();
        }
        LogHelper.info("Bounds: X: %f Y: %f", bounds.getMaxX(), bounds.getMaxY());
        LookupHelper.Point2D coords = dialog.getOutSceneCoords(bounds);
        stage.setX(coords.x);
        stage.setY(coords.y);
    }
}
