package pro.gravit.launcher.client.gui.stage;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.AbstractStage;

public class ConsoleStage extends AbstractStage {
    public ConsoleStage(JavaFXApplication application) {
        super(application.newStage());
        stage.setTitle(application.config.projectName.concat(" Launcher Console"));
        stage.setResizable(false);
    }
}
