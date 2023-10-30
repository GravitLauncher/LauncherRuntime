package pro.gravit.launcher.client.gui.overlays;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.DesignConstants;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.utils.JavaFxUtils;
import pro.gravit.utils.helper.LogHelper;

public class WelcomeOverlay extends AbstractOverlay {
    public WelcomeOverlay(JavaFXApplication application) {
        super("overlay/welcome/welcome.fxml", application);
    }

    @Override
    public String getName() {
        return "welcome";
    }

    @Override
    protected void doInit() {
        reset();
    }

    @Override
    public void reset() {
        LookupHelper.<Label>lookupIfPossible(layout, "#playerName")
                    .ifPresent((e) -> e.setText(application.authService.getUsername()));
        LookupHelper.<ImageView>lookupIfPossible(layout, "#playerHead").ifPresent((h) -> {
            try {
                JavaFxUtils.setStaticRadius(h, DesignConstants.AVATAR_IMAGE_RADIUS);
                Image image = application.skinManager.getScaledFxSkinHead(
                        application.authService.getUsername(), (int) h.getFitWidth(), (int) h.getFitHeight());
                if (image != null) h.setImage(image);
            } catch (Throwable e) {
                LogHelper.warning("Skin head error");
            }
        });
    }
}
