package pro.gravit.launcher.gui.core.service;

import javafx.scene.image.Image;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;

import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerButtonStateService {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private static final String SERVER_BUTTON_DEFAULT_IMAGE = "images/servers/example.png";
    private static final String SERVER_BUTTON_CUSTOM_IMAGE = "images/servers/%s.png";

    private final JavaFXApplication application;
    private final PingService pingService;
    private final Map<UUID, ServerButtonState> states = new ConcurrentHashMap<>();
    private final Map<UUID, String> fxmlPathCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<Image>> imageCache = new ConcurrentHashMap<>();

    public ServerButtonStateService(JavaFXApplication application, PingService pingService) {
        this.application = application;
        this.pingService = pingService;
    }

    public ServerButtonState getState(ProfileFeatureAPI.ClientProfile profile) {
        return states.compute(profile.getUUID(), (profileId, currentState) -> {
            String fxmlPath = resolveServerButtonFxml(profile);
            ResolvedImage resolvedImage = resolveServerButtonImage(profile);
            if (currentState == null) {
                return new ServerButtonState(profile, fxmlPath, resolvedImage.path(), resolvedImage.image(),
                        pingService.getPingReportProperty(profileId));
            }
            currentState.updateProfile(profile, fxmlPath, resolvedImage.path(), resolvedImage.image());
            return currentState;
        });
    }

    public void clear() {
        states.clear();
        fxmlPathCache.clear();
        imageCache.clear();
    }

    public void pruneProfiles(Set<UUID> activeProfileIds) {
        states.keySet().removeIf(profileId -> !activeProfileIds.contains(profileId));
        fxmlPathCache.keySet().removeIf(profileId -> !activeProfileIds.contains(profileId));
    }

    private String resolveServerButtonFxml(ProfileFeatureAPI.ClientProfile profile) {
        return fxmlPathCache.computeIfAbsent(profile.getUUID(), profileId -> {
            String customFxml = String.format(SERVER_BUTTON_CUSTOM_FXML, profileId);
            return application.tryResource(customFxml) != null ? customFxml : SERVER_BUTTON_FXML;
        });
    }

    private ResolvedImage resolveServerButtonImage(ProfileFeatureAPI.ClientProfile profile) {
        String customImagePath = String.format(SERVER_BUTTON_CUSTOM_IMAGE, profile.getUUID());
        Image customImage = getCachedImage(customImagePath);
        if (customImage != null) {
            return new ResolvedImage(customImagePath, customImage);
        }
        Image defaultImage = getCachedImage(SERVER_BUTTON_DEFAULT_IMAGE);
        return new ResolvedImage(defaultImage == null ? "" : SERVER_BUTTON_DEFAULT_IMAGE, defaultImage);
    }

    private Image getCachedImage(String resourcePath) {
        return imageCache.computeIfAbsent(resourcePath, path -> {
            URL resource = application.tryResource(path);
            if (resource == null) {
                return Optional.empty();
            }
            return Optional.of(new Image(resource.toString()));
        }).orElse(null);
    }

    private record ResolvedImage(String path, Image image) {
    }
}
