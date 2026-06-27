package pro.gravit.launcher.gui.scenes.servermenu;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;

import java.util.*;

public class ServerMenuScene extends FxScene implements SceneSupportUserBlock {
    private static final Logger logger =
            LoggerFactory.getLogger(ServerMenuScene.class);

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
        isResetOnShow = true;
    }

    @Override
    public void reset() {
        List<ProfileFeatureAPI.ClientProfile> profiles = application.profileService.getProfiles() == null
                ? new ArrayList<>()
                : new ArrayList<>(application.profileService.getProfiles());
        profiles.sort(Comparator.comparing(ProfileFeatureAPI.ClientProfile::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        application.serverButtonCacheService.syncProfiles(profiles);
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        HBox serverList = (HBox) scrollPane.getContent();
        serverList.setSpacing(20);
        serverList.getChildren().clear();
        int createdButtons = 0;
        int reusedButtons = 0;
        for (int index = 0; index < profiles.size(); index++) {
            ProfileFeatureAPI.ClientProfile profile = profiles.get(index);
            if (application.serverButtonCacheService.contains(profile.getUUID())) {
                reusedButtons++;
            } else {
                createdButtons++;
            }
            application.serverButtonCacheService.attachMenuButton(profile, serverList, index,
                    createServerButtonClickHandler(profile));
        }
        refreshServerPings(profiles);
        logger.debug("ServerMenu reset profileCount={} createdButtons={} reusedButtons={} currentProfile={}",
                profiles.size(), createdButtons, reusedButtons,
                application.profileService.getCurrentProfile() == null
                        ? "null"
                        : application.profileService.getCurrentProfile().getName());
        userBlock.reset();
    }

    private EventHandler<MouseEvent> createServerButtonClickHandler(ProfileFeatureAPI.ClientProfile profile) {
        return event -> {
            if (!event.getButton().equals(MouseButton.PRIMARY)) {
                return;
            }
            changeServer(profile);
            try {
                switchScene(application.gui.serverInfoScene);
            } catch (Exception e) {
                errorHandle(e);
            }
        };
    }

    private void refreshServerPings(List<ProfileFeatureAPI.ClientProfile> profiles) {
        for (ProfileFeatureAPI.ClientProfile profile : profiles) {
            LauncherBackendAPIHolder.getApi().pingProfileServers(profile).thenAccept(result ->
                    contextHelper.runInFxThread(() -> application.pingService.addReport(profile.getUUID(), result)));
        }
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
