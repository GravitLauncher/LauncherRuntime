package pro.gravit.launcher.gui.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import pro.gravit.launcher.gui.JavaFXApplication;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;

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

    public static void setRadius(Region node, double radius) {
        setRadius(node, radius, radius);
    }

    public static void setRadius(Region node, double width, double height) {
        Rectangle r = new Rectangle(30, 30);
        r.setArcWidth(width);
        r.setArcHeight(height);
        node.setClip(r); // Or setShape (?)
        node.widthProperty().addListener(p -> r.setWidth(node.getWidth()));
        node.heightProperty().addListener(p -> r.setHeight(node.getHeight()));
    }

    public static void setStaticRadius(ImageView node, double radius) {
        setStaticRadius(node, radius, radius);
    }

    public static void setStaticRadius(ImageView node, double width, double height) {
        Rectangle r = new Rectangle(node.getFitWidth(), node.getFitHeight());
        r.setArcWidth(width);
        r.setArcHeight(height);
        node.setClip(r);
    }

    public static URL getStyleUrl(String url) throws IOException {
        URL globalCss;
        try {
            globalCss = JavaFXApplication.getResourceURL(url+".bss");
        } catch (FileNotFoundException | NoSuchFileException e) {
            globalCss = JavaFXApplication.getResourceURL(url+".css");
        }
        return globalCss;
    }
}
