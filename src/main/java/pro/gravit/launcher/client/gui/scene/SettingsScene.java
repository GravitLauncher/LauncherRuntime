package pro.gravit.launcher.client.gui.scene;

import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.stage.Stage;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;

public class SettingsScene extends AbstractScene {
    public Node layout;
    public SettingsScene(Stage stage, JavaFXApplication application) {
        super("scenes/settings/settings.fxml", stage, application);
    }

    @Override
    protected void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(),  "#settingsPane");
        sceneBaseInit(layout);
        ((ButtonBase)layout.lookup("#apply")).setOnAction((e) -> {
            contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run();
        });
    }
}
