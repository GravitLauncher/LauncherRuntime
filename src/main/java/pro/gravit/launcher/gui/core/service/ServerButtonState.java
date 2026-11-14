package pro.gravit.launcher.gui.core.service;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;

import java.util.UUID;

public class ServerButtonState implements AutoCloseable {
    private final javafx.util.Subscription subscription;
    @Override public void close() { subscription.unsubscribe(); }
    private final UUID profileId;
    private final ObjectProperty<ProfileFeatureAPI.ClientProfile> profile = new SimpleObjectProperty<>();
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty minecraftVersion = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty fxmlPath = new SimpleStringProperty("");
    private final StringProperty imagePath = new SimpleStringProperty("");
    private final ObjectProperty<Image> logo = new SimpleObjectProperty<>();
    private final IntegerProperty playersOnline = new SimpleIntegerProperty(0);
    private final IntegerProperty maxPlayers = new SimpleIntegerProperty(0);
    private final BooleanProperty pingAvailable = new SimpleBooleanProperty(false);
    private final StringProperty onlineText = new SimpleStringProperty("?");
    public ServerButtonState(ProfileFeatureAPI.ClientProfile profile, String fxmlPath, String imagePath, Image logo,
            ObservableValue<PingService.PingServerReport> pingReport) {
        this.profileId = profile.getUUID();
        subscription = pingReport.subscribe(this::updatePingReport);
        updateProfile(profile, fxmlPath, imagePath, logo);
        updatePingReport(pingReport.getValue());
    }

    public void updateProfile(ProfileFeatureAPI.ClientProfile profile, String fxmlPath, String imagePath, Image logo) {
        this.profile.set(profile);
        this.name.set(safe(profile.getName()));
        this.minecraftVersion.set(safe(profile.getMinecraftVersion()));
        this.description.set(safe(profile.getDescription()));
        this.fxmlPath.set(safe(fxmlPath));
        this.imagePath.set(safe(imagePath));
        this.logo.set(logo);
    }

    private void updatePingReport(PingService.PingServerReport report) {
        if (report == null) {
            playersOnline.set(0);
            maxPlayers.set(0);
            pingAvailable.set(false);
            onlineText.set("?");
            return;
        }
        playersOnline.set(report.playersOnline);
        maxPlayers.set(report.maxPlayers);
        pingAvailable.set(true);
        onlineText.set(Integer.toString(report.playersOnline));
    }

    public UUID getProfileId() {
        return profileId;
    }

    public ProfileFeatureAPI.ClientProfile getProfile() {
        return profile.get();
    }

    public javafx.beans.property.ReadOnlyObjectProperty<ProfileFeatureAPI.ClientProfile> profileProperty() {
        return profile;
    }

    public String getName() {
        return name.get();
    }

    public javafx.beans.property.ReadOnlyStringProperty nameProperty() {
        return name;
    }

    public javafx.beans.property.ReadOnlyStringProperty minecraftVersionProperty() {
        return minecraftVersion;
    }

    public javafx.beans.property.ReadOnlyStringProperty descriptionProperty() {
        return description;
    }

    public String getFxmlPath() {
        return fxmlPath.get();
    }

    public javafx.beans.property.ReadOnlyStringProperty fxmlPathProperty() {
        return fxmlPath;
    }

    public String getImagePath() {
        return imagePath.get();
    }

    public javafx.beans.property.ReadOnlyStringProperty imagePathProperty() {
        return imagePath;
    }

    public javafx.beans.property.ReadOnlyObjectProperty<Image> logoProperty() {
        return logo;
    }

    public javafx.beans.property.ReadOnlyIntegerProperty playersOnlineProperty() {
        return playersOnline;
    }

    public javafx.beans.property.ReadOnlyIntegerProperty maxPlayersProperty() {
        return maxPlayers;
    }

    public javafx.beans.property.ReadOnlyBooleanProperty pingAvailableProperty() {
        return pingAvailable;
    }

    public javafx.beans.property.ReadOnlyStringProperty onlineTextProperty() {
        return onlineText;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
