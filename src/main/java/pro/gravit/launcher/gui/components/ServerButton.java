package pro.gravit.launcher.gui.components;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FXApplication;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.launcher.gui.basic.ResourcePath;

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
        onProfile(profile);
    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile) {
        name.setText(profile.getName());
        description.setText(profile.getDescription());
        LauncherBackendAPIHolder.getApi().pingServer(profile).thenAcceptAsync((info) -> {
            online.setText(String.format("%d", info.getOnline()));
        }, FxThreadExecutor.getInstance());
    }

    public void setOnSave(Runnable runnable) {
        save.setOnAction((e) -> runnable.run());
    }

    public void setOnReset(Runnable runnable) {
        reset.setOnAction((e) -> runnable.run());
    }

    public void setOnAction(Runnable runnable) {
        getRoot().setOnMouseClicked((e) -> {
            if(e.getButton() == MouseButton.PRIMARY) {
                runnable.run();
            }
        });
    }
}
