package pro.gravit.launcher.gui.scenes.servermenu;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.launcher.runtime.client.ServerPinger;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.utils.helper.CommonHelper;

import java.io.IOException;
import java.util.*;

public class ServerMenuScene extends AbstractScene implements SceneSupportUserBlock {
    private List<ClientProfile> lastProfiles;
    private UserBlock userBlock;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() {
        this.userBlock = new UserBlock(layout, new SceneAccessor());
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
        if (lastProfiles == application.profilesService.getProfiles()) return;
        lastProfiles = application.profilesService.getProfiles();
        Map<ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        
        List<ClientProfile> profiles = new ArrayList<>(lastProfiles);
        profiles.sort(Comparator.comparingInt(ClientProfile::getSortIndex).thenComparing(ClientProfile::getTitle));
        int position = 0;
        for (ClientProfile profile : profiles) {
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

    private void changeServer(ClientProfile profile) {
        application.profilesService.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
    }
}
