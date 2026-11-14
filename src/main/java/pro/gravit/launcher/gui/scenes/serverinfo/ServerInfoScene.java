package pro.gravit.launcher.gui.scenes.serverinfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.launcher.gui.scenes.update.VisualDownloader;

public class ServerInfoScene extends FxScene implements SceneSupportUserBlock {

    private static final Logger logger =
            LoggerFactory.getLogger(ServerInfoScene.class);

    private ServerButton serverButton;
    private UserBlock userBlock;
    private long launchAttempt;

    public ServerInfoScene(JavaFXApplication application) {
        super("scenes/serverinfo/serverinfo.fxml", application);
    }

    @Override
    protected void doInit() {
        this.userBlock = use(layout, UserBlock::new);
        LookupHelper.lookup(layout, Button.class, "#back").setOnAction((e) -> {
            try {
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });

        LookupHelper.lookup(header, ButtonBase.class, "#controls", "#clientSettings").setOnAction((e) -> {
            try {
                if (application.profileService.getCurrentProfile() == null) return;
                switchScene(application.gui.optionsScene);
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        LookupHelper.lookup(header, ButtonBase.class, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.settingsScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        isResetOnShow = true;
    }

    @Override
    public void reset() {
        launchAttempt++;
        ProfileFeatureAPI.ClientProfile profile = application.profileService.getCurrentProfile();
        LookupHelper.lookupIfPossible(layout, Label.class, "#serverName").ifPresent((e) -> e.setText(profile.getName()));
        LookupHelper.lookupIfPossible(layout, ScrollPane.class, "#serverDescriptionPane").ifPresent((e) -> {
            var label = (Label) e.getContent();
            label.setText(profile.getDescription());
        });
        Pane serverButtonContainer = LookupHelper.lookup(layout, Pane.class, "#serverButton");
        serverButtonContainer.getChildren().clear();
        serverButton = application.serverCardService.attachServerInfoButton(profile, serverButtonContainer,
                application.getTranslation("runtime.scenes.serverinfo.serverButton.game"), e -> runClient());
        this.userBlock.reset();
    }

    private void runClient() {
        if (application.isOperationBusy()) {
            errorHandle(application.getTranslation("runtime.operation.busy", "Finish the current operation first."));
            return;
        }
        var profile = application.profileService.getCurrentProfile();
        if (profile == null) return;
        long attempt = ++launchAttempt;
        contextHelper.runInFxThread(() -> {
            switchScene(application.gui.updateScene);
            application.beginOperation();
            try {
                VisualDownloader downloadSession = application.gui.updateScene.makeDownloadCallback();
                var downloadProfile = LauncherBackendAPIHolder.getApi().downloadProfile(profile,
                        LauncherBackendAPIHolder.getApi().makeClientProfileSettings(profile), downloadSession);
                downloadProfile.whenComplete((readyProfile, error) -> contextHelper.runInFxThread(() -> {
                    try {
                        downloadSession.finish();
                        if (error != null) {
                            if (!downloadSession.isCancelled() && attempt == launchAttempt) application.gui.updateScene.errorHandle(error);
                            return;
                        }
                        if (downloadSession.isCancelled() || attempt != launchAttempt) return;
                        switchScene(application.gui.debugScene);
                        application.gui.debugScene.run(readyProfile);
                    } finally {
                        application.endOperation();
                    }
                }));
            } catch (Throwable error) {
                application.endOperation();
                application.gui.updateScene.errorHandle(error);
            }
        });
    }

    @Override
    public String getName() {
        return "serverinfo";
    }

    @Override
    public UserBlock getUserBlock() {
        return userBlock;
    }
}
