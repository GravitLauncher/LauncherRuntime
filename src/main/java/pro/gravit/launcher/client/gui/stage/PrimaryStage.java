package pro.gravit.launcher.client.gui.stage;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.raw.AbstractStage;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.nio.file.NoSuchFileException;

public class PrimaryStage extends AbstractStage {
    public PrimaryStage(Stage primaryStage, String title) {
        super(primaryStage);
        primaryStage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        // Icons
        try {
            Image icon = new Image(IOHelper.getResourceURL("runtime/favicon.png").toString());
            stage.getIcons().add(icon);
        } catch (NoSuchFileException e) {
            LogHelper.error(e);
        }
    }

    @Override
    public void close() {
        Platform.exit();
    }
}
