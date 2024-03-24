package pro.gravit.launcher.gui.stage;

import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.DesignConstants;
import pro.gravit.launcher.gui.impl.AbstractStage;

public class ConsoleStage extends AbstractStage {
    public ConsoleStage(JavaFXApplication application) {
        super(application, application.newStage());
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(true);
        scene.setFill(Color.TRANSPARENT);
        stage.setTitle("%s Launcher Console".formatted(application.config.projectName));
        stage.setResizable(false);
        setClipRadius(DesignConstants.SCENE_CLIP_RADIUS, DesignConstants.SCENE_CLIP_RADIUS);
    }
}
