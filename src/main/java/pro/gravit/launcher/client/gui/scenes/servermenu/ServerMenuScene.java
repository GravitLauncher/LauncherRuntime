package pro.gravit.launcher.client.gui.scenes.servermenu;

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.*;

public class ServerMenuScene extends AbstractScene {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private ImageView avatar;

    private List<ClientProfile> lastProfiles;
    private Image originalAvatarImage;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    public static boolean putAvatarToImageView(JavaFXApplication application, String username, ImageView imageView) {
        int width = (int) imageView.getFitWidth();
        int height = (int) imageView.getFitHeight();
        Image head = application.skinManager.getScaledFxSkinHead(username, width, height);
        if (head == null) return false;
        imageView.setImage(head);
        return true;
    }

    public static ServerButtonComponent getServerButton(JavaFXApplication application, ClientProfile profile) {
        return new ServerButtonComponent(application, profile);
    }

    @Override
    public void doInit() throws Exception {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent(
                (h) -> {
                    try {
                        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(h.getFitWidth(), h.getFitHeight());
                        clip.setArcWidth(h.getFitWidth());
                        clip.setArcHeight(h.getFitHeight());
                        h.setClip(clip);
                        h.setImage(originalAvatarImage);
                    } catch (Throwable e) {
                        LogHelper.warning("Skin head error");
                    }
                }
        );
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        scrollPane.setOnScroll(e -> {
            double offset = e.getDeltaY() / scrollPane.getWidth();
            scrollPane.setHvalue(scrollPane.getHvalue() - offset);
        });
        reset();
        isResetOnShow = true;
    }

    @Override
    public void reset() {
        if (lastProfiles == application.stateService.getProfiles()) return;
        lastProfiles = application.stateService.getProfiles();
        Map<ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname").ifPresent((e) -> e.setText(application.stateService.getUsername()));
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
            serverButtonCache.serverButton.addTo(serverList, serverButtonCache.position);
            serverButtonCache.serverButton.setOnMouseClicked(handle);
        });
        CommonHelper.newThread("ServerPinger", true, () -> {
            for (ClientProfile profile : lastProfiles) {
                ClientProfile.ServerProfile serverProfile = profile.getDefaultServerProfile();
                if (!serverProfile.socketPing || serverProfile.serverAddress == null) continue;
                try {
                    ServerPinger pinger = new ServerPinger(serverProfile, profile.getVersion());
                    ServerPinger.Result result = pinger.ping();
                    contextHelper.runInFxThread(() -> {
                        application.pingService.addReport(serverProfile.name, result);
                    });
                } catch (IOException ignored) {
                }
            }
        }).start();
        putAvatarToImageView(application, application.stateService.getUsername(), avatar);
    }

    @Override
    public String getName() {
        return "serverMenu";
    }

    private void changeServer(ClientProfile profile) {
        application.stateService.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
    }

    static class ServerButtonCache {
        public ServerButtonComponent serverButton;
        public int position;
    }
}
