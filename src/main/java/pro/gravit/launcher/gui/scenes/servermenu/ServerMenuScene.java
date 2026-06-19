package pro.gravit.launcher.gui.scenes.servermenu;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.utils.helper.CommonHelper;

import java.util.*;

public class ServerMenuScene extends FxScene implements SceneSupportUserBlock {
    private UserBlock userBlock;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() {
        this.userBlock = use(layout, UserBlock::new);
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.globalSettingsScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        scrollPane.setOnScroll(e -> {
            double widthContent = scrollPane.getWidth();
            double offset = (widthContent * 0.15) / (scrollPane.getContent().getBoundsInLocal().getWidth() - widthContent) * Math.signum(e.getDeltaY());
            scrollPane.setHvalue(scrollPane.getHvalue() - offset);
        });
        reset();
        isResetOnShow = true;
    }

    static class ServerButtonCache {
        public ServerButton serverButton;
        public int position;
    }

    @Override
    public void reset() {
        Map<ProfileFeatureAPI.ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        
        List<ProfileFeatureAPI.ClientProfile> profiles = new ArrayList<>(application.profileService.getProfiles());
        profiles.sort(Comparator.comparing(ProfileFeatureAPI.ClientProfile::getName));
        int position = 0;
        for (var profile : profiles) {
            ServerButtonCache cache = new ServerButtonCache();
            cache.serverButton = ServerButton.createServerButton(application, profile);
            cache.position = position;
            serverButtonCacheMap.put(profile, cache);
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
        for (ProfileFeatureAPI.ClientProfile profile : profiles) {
            LauncherBackendAPIHolder.getApi().pingProfileServers(profile).thenAccept((result) -> {
                contextHelper.runInFxThread(
                        () -> application.pingService.addReport(profile.getUUID(), result));
            });
        }
        CommonHelper.newThread("ServerPinger", true, () -> {
        }).start();
        userBlock.reset();
    }

    @Override
    public UserBlock getUserBlock() {
        return userBlock;
    }

    @Override
    public String getName() {
        return "serverMenu";
    }

    private void changeServer(ProfileFeatureAPI.ClientProfile profile) {
        application.profileService.setCurrentProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
    }
}
