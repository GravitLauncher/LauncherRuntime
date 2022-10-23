package pro.gravit.launcher.client.gui.impl;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.client.gui.scenes.options.OptionsScene;
import pro.gravit.launcher.client.gui.scenes.serverinfo.ServerInfoScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.client.gui.scenes.settings.SettingsScene;
import pro.gravit.launcher.events.RequestEvent;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.RequestService;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.websockets.ClientWebSocketService;
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
        if (event instanceof RequestEvent) {
            if (!((RequestEvent) event).requestUUID.equals(RequestEvent.eventUUID))
                return false;
        }
        try {
            if (event instanceof AuthRequestEvent) {
                boolean isNextScene = application.getCurrentScene() instanceof LoginScene;
                ((LoginScene) application.getCurrentScene()).isLoginStarted = true;
                LogHelper.dev("Receive auth event. Send next scene %s", isNextScene ? "true" : "false");
                application.stateService.setAuthResult(null, (AuthRequestEvent) event);
                if (isNextScene && ((LoginScene) application.getCurrentScene()).isLoginStarted)
                    ((LoginScene) application.getCurrentScene()).onGetProfiles();
            }
            if(event instanceof ProfilesRequestEvent) {
                application.stateService.setProfilesResult((ProfilesRequestEvent) event);
                if(application.stateService.getProfile() != null) {
                    UUID profileUUID = application.stateService.getProfile().getUUID();
                    for(ClientProfile profile : application.stateService.getProfiles()) {
                        if(profile.getUUID().equals(profileUUID)) {
                            application.stateService.setProfile(profile);
                            break;
                        }
                    }
                }
                AbstractScene scene = application.getCurrentScene();
                if(scene instanceof ServerMenuScene || scene instanceof ServerInfoScene || scene instanceof SettingsScene | scene instanceof OptionsScene) {
                    scene.contextHelper.runInFxThread(scene::reset);
                }
            }
        } catch (Throwable e) {
            LogHelper.error(e);
        }
        return false;
    }
}
