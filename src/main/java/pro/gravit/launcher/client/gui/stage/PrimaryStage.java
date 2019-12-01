package pro.gravit.launcher.client.gui.stage;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.raw.AbstractStage;

public class PrimaryStage extends AbstractStage {
    public PrimaryStage(Stage primaryStage, String title) {
        super(primaryStage);
        primaryStage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
    }
}
