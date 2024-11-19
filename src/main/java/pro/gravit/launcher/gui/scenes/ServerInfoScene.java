package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.LayerPositions;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.basic.Scenes;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.components.UserBlock;

@ResourcePath("scenes/serverinfo/serverinfo.fxml")
public class ServerInfoScene extends FxScene {
    private Pane serverButton;
    private ServerButton serverButtonObj;
    private volatile ProfileFeatureAPI.ClientProfile profile;
    private volatile LauncherBackendAPI.ClientProfileSettings settings;
    @Override
    protected void doInit() {
        super.doInit();
        serverButton = lookup("#serverButton");
        serverButtonObj = new ServerButton();
        inject(serverButton, serverButtonObj);
        use(lookup("#userBlock"), UserBlock::new);
        this.<Button>lookupIfPossible("#back").ifPresent(btn -> btn.setOnAction(e -> {
            this.stage.back();
        }));
        this.<Button>lookupIfPossible("#settings").ifPresent(btn -> btn.setOnAction(e -> {
            this.stage.pushLayer(LayerPositions.SCENE, Scenes.SETTINGS);
            Scenes.SETTINGS.onProfile(profile, settings);
        }));
        this.<Button>lookupIfPossible("#clientSettings").ifPresent(btn -> btn.setOnAction(e -> {
            this.stage.pushLayer(LayerPositions.SCENE, Scenes.OPTIONALS);
            Scenes.OPTIONALS.onProfile(profile, settings);
        }));
    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        serverButtonObj.onProfile(profile);
        serverButtonObj.setOnSave(() -> {
            launch(profile, LauncherBackendAPIHolder.getApi().makeClientProfileSettings(profile));
        });
        this.profile = profile;
        this.settings = settings;
    }

    public void launch(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        var stage = this.stage;
        stage.pushLayer(LayerPositions.SCENE, Scenes.UPDATE);
        Scenes.UPDATE.startDownload(profile, settings).thenAcceptAsync((readyProfile -> {
            stage.pushLayer(LayerPositions.SCENE, Scenes.CLIENT);
            Scenes.CLIENT.runClient(readyProfile);
        }), FxThreadExecutor.getInstance()).exceptionally(this::errorHandleFuture);
    }
}
