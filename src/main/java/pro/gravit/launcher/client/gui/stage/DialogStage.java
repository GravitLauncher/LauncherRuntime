package pro.gravit.launcher.client.gui.stage;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.dialogs.AbstractDialog;
import pro.gravit.launcher.client.gui.impl.AbstractStage;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;

public class DialogStage extends AbstractStage {
    public DialogStage(JavaFXApplication application, String title, AbstractDialog dialog) throws Exception {
        super(application.newStage());
        stage.setTitle(title);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        // Icons
        try {
            Image icon = new Image(JavaFXApplication.getResourceURL("favicon.png").toString());
            stage.getIcons().add(icon);
        } catch (IOException e) {
            LogHelper.error(e);
        }
        setScene(dialog);
    }
}
