package pro.gravit.launcher.gui.scenes.servermenu;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
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
    private ScrollPane servers;
    private HBox serverList;
    private long pingGeneration;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() {
        this.userBlock = use(layout, UserBlock::new);
        LookupHelper.lookup(header, ButtonBase.class, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.globalSettingsScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        ScrollPane scrollPane = LookupHelper.lookup(layout, ScrollPane.class, "#servers");
        servers = scrollPane;
        serverList = (HBox) scrollPane.getContent();
        scrollPane.setOnScroll(e -> {
            double widthContent = scrollPane.getWidth();
            if (scrollPane.getContent().getBoundsInLocal().getWidth() <= widthContent) return;
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
        application.serverCardService.syncProfiles(profiles);
        ScrollPane scrollPane = LookupHelper.lookup(layout, ScrollPane.class, "#servers");
        HBox serverList = (HBox) scrollPane.getContent();
        serverList.setSpacing(20);
        var desiredIds = profiles.stream().map(ProfileFeatureAPI.ClientProfile::getUUID).collect(java.util.stream.Collectors.toSet());
        serverList.getChildren().removeIf(node -> !desiredIds.contains(node.getUserData()));
        int createdButtons = 0;
        int reusedButtons = 0;
        for (int index = 0; index < profiles.size(); index++) {
            ProfileFeatureAPI.ClientProfile profile = profiles.get(index);
            if (application.serverCardService.contains(profile.getUUID())) {
                reusedButtons++;
            } else {
                createdButtons++;
            }
            application.serverCardService.attachMenuButton(profile, serverList, index,
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

    private EventHandler<javafx.event.ActionEvent> createServerButtonClickHandler(ProfileFeatureAPI.ClientProfile profile) {
        return event -> {
            changeServer(profile);
            try {
                switchScene(application.gui.serverInfoScene);
            } catch (Exception e) {
                errorHandle(e);
            }
        };
    }

    private void refreshServerPings(List<ProfileFeatureAPI.ClientProfile> profiles) {
        long request = ++pingGeneration;
        for (ProfileFeatureAPI.ClientProfile profile : profiles) {
            LauncherBackendAPIHolder.getApi().pingProfileServers(profile).whenComplete((result, error) ->
                    contextHelper.runInFxThread(() -> {
                        if (request != pingGeneration) return;
                        if (error == null) application.pingService.addReport(profile.getUUID(), result);
                    }));
        }
    }

    @Override public void onShow() {
        super.onShow();
        visibleScope.subscribe(javafx.util.Subscription.EMPTY);
        javafx.collections.ListChangeListener<ProfileFeatureAPI.ClientProfile> listener = change -> reset();
        application.profileService.getProfiles().addListener(listener);
        visibleScope.add(() -> application.profileService.getProfiles().removeListener(listener));
    }
    @Override public void onHide() { pingGeneration++; super.onHide(); }

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
