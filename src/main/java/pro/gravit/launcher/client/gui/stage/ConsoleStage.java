package pro.gravit.launcher.client.gui.stage;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.impl.AbstractStage;

public class ConsoleStage extends AbstractStage {
    public ConsoleStage(JavaFXApplication application) {
        super(application.newStage());
        stage.setTitle(String.format("%s Launcher Console", application.config.projectName));
        stage.setResizable(false);
    }
}
