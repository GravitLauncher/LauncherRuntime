package pro.gravit.launcher.client.gui.scene;

import javafx.scene.Node;
import javafx.stage.Stage;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.AbstractScene;

public class ConsoleScene extends AbstractScene {
    public Node layout;
    public ConsoleScene(JavaFXApplication application) {
        super("scenes/console/console.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        layout = scene.getRoot();
        sceneBaseInit(layout);
    }
}
