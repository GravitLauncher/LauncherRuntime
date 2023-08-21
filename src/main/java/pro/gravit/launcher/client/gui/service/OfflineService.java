package pro.gravit.launcher.client.gui.service;

import pro.gravit.launcher.ClientPermissions;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.password.AuthOAuthPassword;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.launcher.request.websockets.OfflineRequestService;
import pro.gravit.utils.helper.SecurityHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OfflineService {
    private final JavaFXApplication application;

    public OfflineService(JavaFXApplication application) {
        this.application = application;
    }

    public boolean isAvailableOfflineMode() {
        if (application.guiModuleConfig.disableOfflineMode) {
            return false;
        }
        if (application.runtimeSettings.profiles != null) {
            return true;
        }
        return false;
    }

    public boolean isOfflineMode() {
        return Request.getRequestService() instanceof OfflineRequestService;
    }

    public static void applyRuntimeProcessors(OfflineRequestService service) {
        service.registerRequestProcessor(
                AuthRequest.class, (r) -> {
                    var permissions = new ClientPermissions();
                    String login = r.login;
                    if(login == null && r.password instanceof AuthOAuthPassword oAuthPassword) {
                        login = oAuthPassword.accessToken;
                    }
                    if(login == null) {
                        login = "Player";
                    }
                    return new AuthRequestEvent(
                            permissions,
                            new PlayerProfile(
                                    UUID.nameUUIDFromBytes(login.getBytes(StandardCharsets.UTF_8)), login,
                                    new HashMap<>(), new HashMap<>()), SecurityHelper.randomStringToken(), "", null,
                            new AuthRequestEvent.OAuthRequestEvent(
                                    login, null, 0));
                });
        service.registerRequestProcessor(
                ProfilesRequest.class, (r) -> {
                    JavaFXApplication application = JavaFXApplication.getInstance();
                    List<ClientProfile> profileList =
                            application.runtimeSettings.profiles.stream()
                                                                .filter(profile -> Files.exists(
                                                                        DirBridge.dirUpdates.resolve(profile.getDir()))
                                                                        && Files.exists(DirBridge.dirUpdates.resolve(
                                                                        profile.getAssetDir())))
                                                                .collect(Collectors.toList());
                    return new ProfilesRequestEvent(profileList);
                });
    }
}
