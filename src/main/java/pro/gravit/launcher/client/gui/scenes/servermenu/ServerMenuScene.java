package pro.gravit.launcher.client.gui.scenes.servermenu;

import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.DesignConstants;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.utils.JavaFxUtils;
import pro.gravit.launcher.events.request.GetAssetUploadUrlRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.cabinet.AssetUploadInfoRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.*;

public class ServerMenuScene extends AbstractScene {
    private ImageView avatar;

    private List<ClientProfile> lastProfiles;
    private Image originalAvatarImage;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent((h) -> {
            try {
                JavaFxUtils.setStaticRadius(h, DesignConstants.AVATAR_IMAGE_RADIUS);
                h.setImage(originalAvatarImage);
            } catch (Throwable e) {
                LogHelper.warning("Skin head error");
            }
        });
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        scrollPane.setOnScroll(e -> {
            double offset = e.getDeltaY() / scrollPane.getWidth();
            scrollPane.setHvalue(scrollPane.getHvalue() - offset);
        });
        reset();
        isResetOnShow = true;
    }

    static class ServerButtonCache {
        public ServerButton serverButton;
        public int position;
    }

    public static ServerButton getServerButton(JavaFXApplication application, ClientProfile profile) {
        return new ServerButton(application, profile);
    }

    @Override
    public void reset() {
        if (lastProfiles == application.profilesService.getProfiles()) return;
        lastProfiles = application.profilesService.getProfiles();
        Map<ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname")
                    .ifPresent((e) -> e.setText(application.authService.getUsername()));
        LookupHelper.<Label>lookupIfPossible(layout, "#role")
                    .ifPresent((e) -> e.setText(application.authService.getMainRole()));
        avatar.setImage(originalAvatarImage);
        List<ClientProfile> profiles = new ArrayList<>(lastProfiles);
        profiles.sort(Comparator.comparingInt(ClientProfile::getSortIndex).thenComparing(ClientProfile::getTitle));
        int position = 0;
        for (ClientProfile profile : profiles) {
            ServerButtonCache cache = new ServerButtonCache();
            cache.serverButton = getServerButton(application, profile);
            cache.position = position;
            serverButtonCacheMap.put(profile, cache);
            profile.updateOptionalGraph();
            position++;
        }
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        HBox serverList = (HBox) scrollPane.getContent();
        serverList.setSpacing(20);
        serverList.getChildren().clear();
        application.pingService.clear();
        serverButtonCacheMap.forEach((profile, serverButtonCache) -> {
            EventHandler<? super MouseEvent> handle = (event) -> {
                if (!event.getButton().equals(MouseButton.PRIMARY)) return;
                changeServer(profile);
                try {
                    switchScene(application.gui.serverInfoScene);
                    application.gui.serverInfoScene.reset();
                } catch (Exception e) {
                    errorHandle(e);
                }
            };
            serverButtonCache.serverButton.addTo(serverList, serverButtonCache.position);
            serverButtonCache.serverButton.setOnMouseClicked(handle);
        });
        CommonHelper.newThread("ServerPinger", true, () -> {
            for (ClientProfile profile : lastProfiles) {
                for (ClientProfile.ServerProfile serverProfile : profile.getServers()) {
                    if (!serverProfile.socketPing || serverProfile.serverAddress == null) continue;
                    try {
                        ServerPinger pinger = new ServerPinger(serverProfile, profile.getVersion());
                        ServerPinger.Result result = pinger.ping();
                        contextHelper.runInFxThread(
                                () -> application.pingService.addReport(serverProfile.name, result));
                    } catch (IOException ignored) {
                    }
                }
            }
        }).start();
        resetAvatar();
        if(application.authService.isFeatureAvailable(GetAssetUploadUrlRequestEvent.FEATURE_NAME)) {
            LookupHelper.<Button>lookupIfPossible(layout, "#customization").ifPresent((h) -> {
                h.setVisible(true);
                h.setOnAction((a) -> {
                    processRequest(application.getTranslation("runtime.overlay.processing.text.uploadassetinfo"), new AssetUploadInfoRequest(), (info) -> {
                        contextHelper.runInFxThread(() -> {
                            showOverlay(application.gui.uploadAssetOverlay, (f) -> {
                                application.gui.uploadAssetOverlay.onAssetUploadInfo(info);
                            });
                        });
                    }, this::errorHandle, (e) -> {});
                });
            });
        }
    }

    public void resetAvatar() {
        if(avatar == null) {
            return;
        }
        JavaFxUtils.putAvatarToImageView(application, application.authService.getUsername(), avatar);
    }

    @Override
    public String getName() {
        return "serverMenu";
    }

    private void changeServer(ClientProfile profile) {
        application.profilesService.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
    }
}
