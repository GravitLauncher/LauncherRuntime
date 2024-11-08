package pro.gravit.launcher.gui.components;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FXApplication;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;

@ResourcePath("components/serverButton.fxml")
public class ServerButton extends Layer {
    private Region logoRegion;
    private Label name;
    private Label description;
    private Label online;
    private Button reset;
    private Button save;
    private ProfileFeatureAPI.ClientProfile profile;

    public ServerButton() {
    }

    public ServerButton(ProfileFeatureAPI.ClientProfile profile) {
        this.profile = profile;
    }

    @Override
    protected void doInit() {
        logoRegion = lookup("#serverLogo");
        name = lookup("#nameServer");
        description = lookup("#genreServer");
        online = lookup("#online");
        reset = lookup("#reset");
        save = lookup("#save");
        if(profile != null) {
            onProfile(profile);
        }
    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile) {
        this.profile = profile;
        name.setText(profile.getName());
        description.setText(profile.getDescription());
        updateLogo(profile);
        LauncherBackendAPIHolder.getApi().pingServer(profile).thenAcceptAsync((info) -> {
            online.setText(String.format("%d", info.getOnline()));
        }, FxThreadExecutor.getInstance());
        reset.setVisible(false);
        save.setVisible(false);
    }

    private void updateLogo(ProfileFeatureAPI.ClientProfile profile) {
        String name = profile.getProperty("runtime.logo");
        if(name == null) {
            name = "example";
        }
        String url = String.format("images/servers/%s.png", name);
        try {
            logoRegion.setBackground(new Background(new BackgroundImage(new Image(FXApplication.getInstance().getResource(url).toString()),
                                                                        BackgroundRepeat.NO_REPEAT,
                                                                        BackgroundRepeat.NO_REPEAT,
                                                                        BackgroundPosition.DEFAULT,
                                                                        new BackgroundSize(0.0, 0.0, true, true, false, true))));
        } catch (IOException e) {
            LogHelper.error(e);
        }
    }

    public void setOnSave(Runnable runnable) {
        save.setOnAction((e) -> runnable.run());
        save.setVisible(true);
    }

    public void setOnReset(Runnable runnable) {
        reset.setOnAction((e) -> runnable.run());
        reset.setVisible(true);
    }

    public void setOnAction(Runnable runnable) {
        getRoot().setOnMouseClicked((e) -> {
            if(e.getButton() == MouseButton.PRIMARY) {
                runnable.run();
            }
        });
    }
}
