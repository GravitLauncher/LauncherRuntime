package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.stage.Stage;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;

public class ServerMenuScene extends AbstractScene {
    public Node layout;
    public ServerMenuScene(Stage stage, JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", stage, application);
    }

    @Override
    public void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(),  "#layout", "#serverMenu");
        sceneBaseInit(layout);
    }
}
