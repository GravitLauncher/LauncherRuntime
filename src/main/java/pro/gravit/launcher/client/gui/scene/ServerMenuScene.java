package pro.gravit.launcher.client.gui.scene;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.launcher.request.management.PingServerRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ServerMenuScene extends AbstractScene {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private ImageView avatar;

    private List<ClientProfile> lastProfiles;
    private Image originalAvatarImage;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() throws Exception {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();

        LookupHelper.<ButtonBase>lookup(header, "#controls", "#clientSettings").setOnAction((e) -> {
            try {
                if (application.runtimeStateMachine.getProfile() == null)
                    return;
                switchScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.runtimeStateMachine.getOptionalView());
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.settingsScene);
            } catch (Exception exception) {
                LogHelper.error(exception);
            }
        });
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#deauth").setOnAction((e) ->
                application.messageManager.showApplyDialog(application.getTranslation("runtime.overlay.settings.exitDialog.header"),
                        application.getTranslation("runtime.overlay.settings.exitDialog.description"), () ->
                                processRequest(application.getTranslation("runtime.overlay.settings.exitDialog.processing"),
                                        new ExitRequest(), (event) -> {
                                            // Exit to main menu
                                            ContextHelper.runInFxThreadStatic(() -> {
                                                hideOverlay(0, null);
                                                application.gui.loginScene.clearPassword();
                                                application.gui.loginScene.reset();
                                                try {
                                                    application.saveSettings();
                                                    application.runtimeStateMachine.exit();
                                                    switchScene(application.gui.loginScene);
                                                } catch (Exception ex) {
                                                    LogHelper.error(ex);
                                                }
                                            });
                                        }, (event) -> {

                                        }), () -> {
                        }, true));
        reset();
    }
    static class ServerButtonCache
    {
        public CompletableFuture<Pane> pane;
        public int position;
    }
    public static boolean putAvatarToImageView(JavaFXApplication application, String username, ImageView imageView) {
        int width = (int) imageView.getFitWidth();
        int height = (int) imageView.getFitHeight();
        Image head = application.skinManager.getScaledFxSkinHead(username, width, height);
        if(head == null) return false;
        imageView.setImage(head);
        return true;
    }
    public static CompletableFuture<Pane> getServerButton(JavaFXApplication application, ClientProfile profile) {
        UUID profileUUID = profile.getUUID();
        if(profileUUID == null) {
            profileUUID = UUID.randomUUID();
            LogHelper.warning("Profile %s UUID null", profileUUID);
        }
        String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profileUUID);
        URL customFxml = application.tryResource(customFxmlName);
        CompletableFuture<Pane> future;
        if (customFxml != null) {
            future = application.fxmlFactory.getAsync(customFxmlName);
        } else {
            future = application.fxmlFactory.getAsync(SERVER_BUTTON_FXML);
        }
        future = future.thenApply(pane -> {
            LookupHelper.<Labeled>lookup(pane, "#nameServer").setText(profile.getTitle());
            LookupHelper.<Labeled>lookup(pane, "#genreServer").setText(profile.getVersion().toString());
            LookupHelper.<ImageView>lookupIfPossible(pane, "#serverLogo").ifPresent((a) -> {
                try {
                    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(a.getFitWidth(), a.getFitHeight());
                    clip.setArcWidth(10.0);
                    clip.setArcHeight(10.0);
                    a.setClip(clip);
                } catch (Throwable e) {
                    LogHelper.error(e);
                }
            });
            application.runtimeStateMachine.addServerPingCallback(profile.getDefaultServerProfile().name, (report) -> {
                LookupHelper.<Text>lookup(pane, "#online").setText(String.valueOf(report.playersOnline));
            });
            return pane;
        });
        return future;
    }

    @Override
    public void reset() {
        lastProfiles = application.runtimeStateMachine.getProfiles();
        Map<ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        LookupHelper.<Labeled>lookup(layout, "#nickname").setText(application.runtimeStateMachine.getUsername());
        avatar.setImage(originalAvatarImage);
        int position = 0;
        for (ClientProfile profile : application.runtimeStateMachine.getProfiles()) {
            ServerButtonCache cache = new ServerButtonCache();
            cache.pane = getServerButton(application, profile);
            cache.position = position;
            serverButtonCacheMap.put(profile, cache);
            profile.updateOptionalGraph();
            position++;
        }
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        HBox serverList = (HBox) scrollPane.getContent();
        serverList.setSpacing(20);
        serverList.getChildren().clear();
        application.runtimeStateMachine.clearServerPingCallbacks();
        serverButtonCacheMap.forEach((profile, serverButtonCache) -> {
            try {
                Pane pane = serverButtonCache.pane.get();
                EventHandler<? super MouseEvent> handle = (event) -> {
                    if (!event.getButton().equals(MouseButton.PRIMARY))
                        return;
                    changeServer(profile);
                    try {
                        switchScene(application.gui.serverInfoScene);
                        application.gui.serverInfoScene.reset();
                    } catch (Exception e) {
                        errorHandle(e);
                    }
                };
                pane.setOnMouseClicked(handle);
                serverList.getChildren().add(pane);
            } catch (InterruptedException | ExecutionException e) {
                LogHelper.error(e);
            }
        });
        try {
            Request.service.request(new PingServerRequest()).thenAccept((event) -> {
                if(event.serverMap != null)
                {
                    event.serverMap.forEach((name, value) -> {
                        application.runtimeStateMachine.setServerPingReport(event.serverMap);
                    });
                }
            }).exceptionally((ex) -> {
                LogHelper.error(ex.getCause());
                return null;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        putAvatarToImageView(application, application.runtimeStateMachine.getUsername(), avatar);
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    @Override
    public String getName() {
        return "serverMenu";
    }

    @Override
    protected void doShow() {
        super.doShow();
        if(lastProfiles != application.runtimeStateMachine.getProfiles())
        {
            reset();
        }
    }

    private void changeServer(ClientProfile profile) {
        application.runtimeStateMachine.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
    }
}
