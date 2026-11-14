package pro.gravit.launcher.gui.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.DesignConstants;
import pro.gravit.launcher.gui.core.impl.FxStage;
import pro.gravit.launcher.gui.core.impl.UIComponent;

import java.io.IOException;

public class PrimaryStage extends FxStage {

    private static final Logger logger =
            LoggerFactory.getLogger(PrimaryStage.class);

    public PrimaryStage(JavaFXApplication application, Stage primaryStage, String title) {
        super(application, primaryStage);
        primaryStage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(true);
        stage.setMinWidth(760); stage.setMinHeight(480);
        scene.setFill(Color.TRANSPARENT);
        // Icons
        try {
            Image icon = new Image(JavaFXApplication.getResourceURL("favicon.png").toString());
            stage.getIcons().add(icon);
        } catch (IOException e) {
            logger.error("", e);
        }
        setClipRadius(DesignConstants.SCENE_CLIP_RADIUS, DesignConstants.SCENE_CLIP_RADIUS);
    }

    public void pushBackground(UIComponent component) {
        backgroundContainer.getChildren().setAll(component.getLayout());
    }

    public void pullBackground(UIComponent component) {
        backgroundContainer.getChildren().remove(component.getLayout());
    }

    @Override
    public void close() {
        Platform.exit();
    }
}
