package pro.gravit.launcher.gui.impl;

import javafx.application.Platform;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.launcher.gui.scenes.options.OptionsScene;
import pro.gravit.launcher.gui.scenes.serverinfo.ServerInfoScene;
import pro.gravit.launcher.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.gui.scenes.settings.SettingsScene;
import pro.gravit.launcher.base.events.RequestEvent;
import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.base.request.RequestService;
import pro.gravit.launcher.base.request.WebSocketEvent;
import pro.gravit.utils.helper.LogHelper;

import java.util.UUID;

public class GuiEventHandler implements RequestService.EventHandler {
    private final JavaFXApplication application;

    public GuiEventHandler(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public <T extends WebSocketEvent> boolean eventHandle(T event) {
        LogHelper.dev("Processing event %s", event.getType());
        if (event instanceof RequestEvent requestEvent) {
            if (!requestEvent.requestUUID.equals(RequestEvent.eventUUID)) return false;
        }
        try {
            if (event instanceof AuthRequestEvent authRequestEvent) {
                boolean isNextScene = application.getCurrentScene() instanceof LoginScene; //TODO: FIX
                LogHelper.dev("Receive auth event. Send next scene %s", isNextScene ? "true" : "false");
                application.authService.setAuthResult(null, authRequestEvent);
                if (isNextScene) {
                    Platform.runLater(() -> {
                        try {
                            ((LoginScene) application.getCurrentScene()).onSuccessLogin(
                                    new AuthFlow.SuccessAuth(authRequestEvent,
                                                             authRequestEvent.playerProfile != null ? authRequestEvent.playerProfile.username : null,
                                                             null));
                        } catch (Throwable e) {
                            LogHelper.error(e);
                        }
                    });
                }
            }
            if (event instanceof ProfilesRequestEvent profilesRequestEvent) {
                application.profilesService.setProfilesResult(profilesRequestEvent);
                if (application.profilesService.getProfile() != null) {
                    UUID profileUUID = application.profilesService.getProfile().getUUID();
                    for (ClientProfile profile : application.profilesService.getProfiles()) {
                        if (profile.getUUID().equals(profileUUID)) {
                            application.profilesService.setProfile(profile);
                            break;
                        }
                    }
                }
                AbstractScene scene = application.getCurrentScene();
                if (scene instanceof ServerMenuScene
                        || scene instanceof ServerInfoScene
                        || scene instanceof SettingsScene | scene instanceof OptionsScene) {
                    scene.contextHelper.runInFxThread(scene::reset);
                }
            }
        } catch (Throwable e) {
            LogHelper.error(e);
        }
        return false;
    }
}
