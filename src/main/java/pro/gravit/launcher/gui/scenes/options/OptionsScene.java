package pro.gravit.launcher.gui.scenes.options;

import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.base.profiles.optional.OptionalView;

public class OptionsScene extends AbstractScene implements SceneSupportUserBlock {
    private OptionsTab optionsTab;
    private UserBlock userBlock;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        this.userBlock = new UserBlock(layout, new SceneAccessor());
        optionsTab = new OptionsTab(application, LookupHelper.lookup(layout, "#tabPane"));
    }

    @Override
    public void reset() {
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.profilesService.getProfile();
        ServerButton serverButton = ServerButton.createServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(null, (e) -> {
            try {
                application.profilesService.setOptionalView(profile, optionsTab.getOptionalView());
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        serverButton.enableResetButton(null, (e) -> {
            optionsTab.clear();
            application.profilesService.setOptionalView(profile, new OptionalView(profile));
            optionsTab.addProfileOptionals(application.profilesService.getOptionalView());
        });
        optionsTab.clear();
        LookupHelper.<Button>lookupIfPossible(layout, "#back").ifPresent(x -> x.setOnAction((e) -> {
            try {
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        optionsTab.addProfileOptionals(application.profilesService.getOptionalView());
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
}
