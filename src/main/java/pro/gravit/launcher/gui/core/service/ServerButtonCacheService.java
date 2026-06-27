package pro.gravit.launcher.gui.core.service;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.core.JavaFXApplication;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerButtonCacheService {
    private static final Logger logger =
            LoggerFactory.getLogger(ServerButtonCacheService.class);

    private final JavaFXApplication application;
    private final Map<UUID, ServerButton> buttons = new ConcurrentHashMap<>();

    public ServerButtonCacheService(JavaFXApplication application) {
        this.application = application;
    }

    public void syncProfiles(Collection<ProfileFeatureAPI.ClientProfile> profiles) {
        Set<UUID> activeProfileIds = new LinkedHashSet<>();
        for (ProfileFeatureAPI.ClientProfile profile : profiles) {
            activeProfileIds.add(profile.getUUID());
            application.serverButtonStateService.getState(profile);
        }
        buttons.keySet().removeIf(profileId -> !activeProfileIds.contains(profileId));
        application.serverButtonStateService.pruneProfiles(activeProfileIds);
    }

    public boolean contains(UUID profileId) {
        return buttons.containsKey(profileId);
    }

    public void clear() {
        buttons.clear();
    }

    public ServerButton attachMenuButton(ProfileFeatureAPI.ClientProfile profile, Pane pane, int position,
            EventHandler<MouseEvent> clickHandler) {
        ServerButton button = getOrCreate(profile);
        button.configureMenuMode(clickHandler);
        button.addTo(pane, position);
        logger.debug("ServerButton attach profile='{}' uuid={} mode=menu position={}",
                profile.getName(), profile.getUUID(), position);
        return button;
    }

    public ServerButton attachServerInfoButton(ProfileFeatureAPI.ClientProfile profile, Pane pane, String saveText,
            EventHandler<ActionEvent> saveHandler) {
        return attachDetailButton(profile, pane, saveText, saveHandler, null, null, "serverinfo");
    }

    public ServerButton attachDetailButton(ProfileFeatureAPI.ClientProfile profile, Pane pane, String saveText,
            EventHandler<ActionEvent> saveHandler, String resetText, EventHandler<ActionEvent> resetHandler,
            String mode) {
        ServerButton button = getOrCreate(profile);
        button.configureDetailMode(resolveSaveText(saveText), saveHandler, resolveResetText(resetText), resetHandler);
        button.addTo(pane);
        logger.debug("ServerButton attach profile='{}' uuid={} mode={}", profile.getName(), profile.getUUID(), mode);
        return button;
    }

    private ServerButton getOrCreate(ProfileFeatureAPI.ClientProfile profile) {
        application.serverButtonStateService.getState(profile);
        return buttons.computeIfAbsent(profile.getUUID(), profileId -> ServerButton.createServerButton(application, profile));
    }

    private String resolveSaveText(String text) {
        return text != null ? text : application.getTranslation("runtime.components.serverButton.save");
    }

    private String resolveResetText(String text) {
        return text != null ? text : application.getTranslation("runtime.components.serverButton.reset");
    }
}
