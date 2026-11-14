package pro.gravit.launcher.gui.overlays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.impl.FxOverlay;

public class WelcomeOverlay extends FxOverlay {

    private static final Logger logger =
            LoggerFactory.getLogger(WelcomeOverlay.class);

    private Image originalImage;
    public WelcomeOverlay(JavaFXApplication application) {
        super("overlay/welcome/welcome.fxml", application);
    }

    @Override
    public String getName() {
        return "welcome";
    }

    @Override
    protected void doInit() {
        var nodes = new pro.gravit.launcher.gui.core.ui.ViewNodes(layout, "overlay/welcome/welcome.fxml");
        nodes.optional(Label.class, "#playerName").ifPresent(label -> {
            label.textProperty().bind(application.authService.username);
            scope.add(() -> label.textProperty().unbind());
        });
        nodes.optional(ImageView.class, "#playerHead").ifPresent(view -> {
            originalImage = view.getImage();
            view.setSmooth(false);
            scope.subscribe(application.skinManager.avatarProperty().subscribe(image -> view.setImage(image == null
                    ? originalImage : application.skinManager.getScaledFxSkinHead(application.authService.getUsername(),
                    (int) view.getFitWidth(), (int) view.getFitHeight()))));
        });
    }

    @Override
    public void reset() {
        // State is supplied by the user and avatar bindings.
    }
}
