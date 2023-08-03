package pro.gravit.launcher.client.gui.overlays;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
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
        LookupHelper.<Label>lookupIfPossible(layout, "#playerName")
                    .ifPresent((e) -> e.setText(application.stateService.getUsername()));
        LookupHelper.<ImageView>lookupIfPossible(layout, "#playerHead").ifPresent((h) -> {
            try {
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(h.getFitWidth(), h.getFitHeight());
                clip.setArcWidth(h.getFitWidth());
                clip.setArcHeight(h.getFitHeight());
                h.setClip(clip);
                Image image = application.skinManager.getScaledFxSkinHead(
                        application.stateService.getUsername(), (int) h.getFitWidth(), (int) h.getFitHeight());
                if (image != null) h.setImage(image);
            } catch (Throwable e) {
                LogHelper.warning("Skin head error");
            }
        });
    }

    @Override
    public void reset() {

    }
}
