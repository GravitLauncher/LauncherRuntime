package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.components.UserBlock;

@ResourcePath("scenes/serverinfo/serverinfo.fxml")
public class ServerInfoScene extends FxScene {
    private Pane serverButton;
    private ServerButton serverButtonObj;
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

    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        serverButtonObj.onProfile(profile);
        serverButtonObj.setOnSave(() -> {

        });
    }
}
