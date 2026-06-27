package pro.gravit.launcher.gui.scenes.options;

import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;

public class OptionsScene extends FxScene implements SceneSupportUserBlock {
    private OptionsTab optionsTab;
    private UserBlock userBlock;
    private LauncherBackendAPI.ClientProfileSettings profileSettings;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        this.userBlock = use(layout, UserBlock::new);
        optionsTab = new OptionsTab(application, LookupHelper.lookup(layout, "#tabPane"));
        isResetOnShow = true;
    }

    @Override
    public void reset() {
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ProfileFeatureAPI.ClientProfile profile = application.profileService.getCurrentProfile();
        if (profile == null) {
            return;
        }
        reloadProfileSettings(profile);
        application.serverButtonCacheService.attachDetailButton(profile, serverButtonContainer, null, e -> {
            try {
                LauncherBackendAPIHolder.getApi().saveClientProfileSettings(profileSettings);
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }, null, e -> {
            reloadProfileSettings(profile);
        }, "options");
        LookupHelper.<Button>lookupIfPossible(layout, "#back").ifPresent(x -> x.setOnAction((e) -> {
            try {
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        userBlock.reset();
    }

    @Override
    public String getName() {
        return "options";
    }

    @Override
    public UserBlock getUserBlock() {
        return userBlock;
    }

    private void reloadProfileSettings(ProfileFeatureAPI.ClientProfile profile) {
        profileSettings = LauncherBackendAPIHolder.getApi().makeClientProfileSettings(profile);
        optionsTab.clear();
        optionsTab.addProfileOptionals(profileSettings);
    }
}
