package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.LayerPositions;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.basic.Scenes;
import pro.gravit.launcher.gui.components.ServerButton;

import java.util.List;

@ResourcePath("scenes/servermenu/servermenu.fxml")
public class ServerMenuScene extends FxScene {
    private Pane content;
    @Override
    protected void doInit() {
        super.doInit();
        content = (Pane) this.<ScrollPane>lookup("#servers").getContent();
        LauncherBackendAPIHolder.getApi().fetchProfiles().thenAcceptAsync(this::onProfiles, FxThreadExecutor.getInstance());
    }

    public void onProfiles(List<ProfileFeatureAPI.ClientProfile> profiles) {
        content.getChildren().clear();
        for(var profile : profiles) {
            var serverButton = inject(content, new ServerButton(profile));
            serverButton.setOnAction(() -> {
                var settings = LauncherBackendAPIHolder.getApi().makeClientProfileSettings(profile);
                stage.pushLayer(LayerPositions.SCENE, Scenes.SERVER_INFO);
                Scenes.SERVER_INFO.onProfile(profile, settings);
            });
        }
    }
}
