package pro.gravit.launcher.client.gui.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import pro.gravit.launcher.client.gui.JavaFXApplication;

public class JavaFxUtils {
    private JavaFxUtils() {

    }

    public static boolean putAvatarToImageView(JavaFXApplication application, String username, ImageView imageView) {
        int width = (int) imageView.getFitWidth();
        int height = (int) imageView.getFitHeight();
        Image head = application.skinManager.getScaledFxSkinHead(username, width, height);
        if (head == null) return false;
        imageView.setImage(head);
        return true;
    }
}
