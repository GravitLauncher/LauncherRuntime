package pro.gravit.launcher.gui.scenes;

import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.components.ServerButton;

@ResourcePath("scenes/serverinfo/serverinfo.fxml")
public class ServerInfoScene extends FxScene {
    @Override
    protected void doInit() {
        super.doInit();

    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        inject(lookup("#serverButton"), new ServerButton(profile));
    }
}
