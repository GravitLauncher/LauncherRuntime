package pro.gravit.launcher.client.gui.stage;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.impl.AbstractStage;

public class ConsoleStage extends AbstractStage {
    public ConsoleStage(JavaFXApplication application) {
        super(application.newStage());
        stage.setTitle("%s Launcher Console".formatted(application.config.projectName));
        stage.setResizable(false);
    }
}
