package pro.gravit.launcher.gui.stage;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.DesignConstants;
import pro.gravit.launcher.gui.impl.AbstractStage;
import pro.gravit.launcher.gui.impl.AbstractVisualComponent;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;

public class PrimaryStage extends AbstractStage {
    public PrimaryStage(JavaFXApplication application, Stage primaryStage, String title) {
        super(application, primaryStage);
        primaryStage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(true);
        scene.setFill(Color.TRANSPARENT);
        // Icons
        try {
            Image icon = new Image(JavaFXApplication.getResourceURL("favicon.png").toString());
            stage.getIcons().add(icon);
        } catch (IOException e) {
            LogHelper.error(e);
        }
        setClipRadius(DesignConstants.SCENE_CLIP_RADIUS, DesignConstants.SCENE_CLIP_RADIUS);
    }

    public void pushBackground(AbstractVisualComponent component) {
        scenePosition.incrementAndGet();
        addBefore(visualComponent.getLayout(), component.getLayout());
    }

    public void pullBackground(AbstractVisualComponent component) {
        scenePosition.decrementAndGet();
        pull(component.getLayout());
    }

    @Override
    public void close() {
        Platform.exit();
    }
}
