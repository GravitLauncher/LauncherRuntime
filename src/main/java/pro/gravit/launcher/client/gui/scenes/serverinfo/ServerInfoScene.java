package pro.gravit.launcher.client.gui.scenes.serverinfo;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerButton;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.client.gui.utils.JavaFxUtils;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.*;

public class ServerInfoScene extends AbstractScene {
    private ImageView avatar;
    private Image originalAvatarImage;
    private ServerButton serverButton;

    public ServerInfoScene(JavaFXApplication application) {
        super("scenes/serverinfo/serverinfo.fxml", application);
    }

    @Override
    protected void doInit() {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent((h) -> {
            try {
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(h.getFitWidth(), h.getFitHeight());
                clip.setArcWidth(h.getFitWidth());
                clip.setArcHeight(h.getFitHeight());
                h.setClip(clip);
                h.setImage(originalAvatarImage);
            } catch (Throwable e) {
                LogHelper.warning("Skin head error");
            }
        });
        LookupHelper.<Button>lookup(header, "#back").setOnAction((e) -> {
            try {
                switchScene(application.gui.serverMenuScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });

        LookupHelper.<ButtonBase>lookup(header, "#controls", "#clientSettings").setOnAction((e) -> {
            try {
                if (application.profilesService.getProfile() == null) return;
                switchScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.profilesService.getOptionalView());
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.settingsScene);
                application.gui.settingsScene.reset();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        reset();
    }

    @Override
    public void reset() {
        avatar.setImage(originalAvatarImage);
        ClientProfile profile = application.profilesService.getProfile();
        LookupHelper.<Label>lookupIfPossible(layout, "#serverName").ifPresent((e) -> e.setText(profile.getTitle()));
        LookupHelper.<Label>lookupIfPossible(layout, "#serverDescription")
                    .ifPresent((e) -> e.setText(profile.getInfo()));
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname")
                    .ifPresent((e) -> e.setText(application.authService.getUsername()));
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        serverButton = ServerMenuScene.getServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(application.getTranslation("runtime.scenes.serverinfo.serverButton.game"),
                                      (e) -> {
                                        runClient();
                                      });
        JavaFxUtils.putAvatarToImageView(application, application.authService.getUsername(), avatar);
    }

    private void runClient() {
        application.launchService.launchClient().thenAccept((clientInstance -> {
            if(clientInstance.getSettings().debug) {
                contextHelper.runInFxThread(() -> {
                    try {
                        switchScene(application.gui.debugScene);
                        application.gui.debugScene.onClientInstance(clientInstance);
                    } catch (Exception ex) {
                        errorHandle(ex);
                    }
                });
            } else {
                clientInstance.start();
                clientInstance.getOnWriteParamsFuture().thenAccept((ok) -> {
                    LogHelper.info("Params write successful. Exit...");
                    Platform.exit();
                }).exceptionally((ex) -> {
                    contextHelper.runInFxThread(() -> {
                        errorHandle(ex);
                    });
                    return null;
                });
            }
        })).exceptionally((ex) -> {
            contextHelper.runInFxThread(() -> {
                errorHandle(ex);
            });
            return null;
        });
    }

    @Override
    public String getName() {
        return null;
    }
}
