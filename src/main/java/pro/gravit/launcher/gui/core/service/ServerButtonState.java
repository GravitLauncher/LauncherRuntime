package pro.gravit.launcher.gui.core.service;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
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
import javafx.scene.input.MouseEvent;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;

import java.util.UUID;

public class ServerButtonState {
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
    private final BooleanProperty saveVisible = new SimpleBooleanProperty(false);
    private final StringProperty saveText = new SimpleStringProperty("");
    private final ObjectProperty<EventHandler<ActionEvent>> saveAction = new SimpleObjectProperty<>();
    private final BooleanProperty resetVisible = new SimpleBooleanProperty(false);
    private final StringProperty resetText = new SimpleStringProperty("");
    private final ObjectProperty<EventHandler<ActionEvent>> resetAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<MouseEvent>> mouseClickedHandler = new SimpleObjectProperty<>();
    private final ObjectProperty<Cursor> cursor = new SimpleObjectProperty<>(Cursor.HAND);

    public ServerButtonState(ProfileFeatureAPI.ClientProfile profile, String fxmlPath, String imagePath, Image logo,
            ObservableValue<PingService.PingServerReport> pingReport) {
        this.profileId = profile.getUUID();
        pingReport.addListener((observable, oldValue, newValue) -> updatePingReport(newValue));
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

    public ObjectProperty<ProfileFeatureAPI.ClientProfile> profileProperty() {
        return profile;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty minecraftVersionProperty() {
        return minecraftVersion;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public String getFxmlPath() {
        return fxmlPath.get();
    }

    public StringProperty fxmlPathProperty() {
        return fxmlPath;
    }

    public String getImagePath() {
        return imagePath.get();
    }

    public StringProperty imagePathProperty() {
        return imagePath;
    }

    public ObjectProperty<Image> logoProperty() {
        return logo;
    }

    public IntegerProperty playersOnlineProperty() {
        return playersOnline;
    }

    public IntegerProperty maxPlayersProperty() {
        return maxPlayers;
    }

    public BooleanProperty pingAvailableProperty() {
        return pingAvailable;
    }

    public StringProperty onlineTextProperty() {
        return onlineText;
    }

    public BooleanProperty saveVisibleProperty() {
        return saveVisible;
    }

    public StringProperty saveTextProperty() {
        return saveText;
    }

    public ObjectProperty<EventHandler<ActionEvent>> saveActionProperty() {
        return saveAction;
    }

    public BooleanProperty resetVisibleProperty() {
        return resetVisible;
    }

    public StringProperty resetTextProperty() {
        return resetText;
    }

    public ObjectProperty<EventHandler<ActionEvent>> resetActionProperty() {
        return resetAction;
    }

    public ObjectProperty<EventHandler<MouseEvent>> mouseClickedHandlerProperty() {
        return mouseClickedHandler;
    }

    public ObjectProperty<Cursor> cursorProperty() {
        return cursor;
    }

    public void configureMenuMode(EventHandler<MouseEvent> clickHandler) {
        mouseClickedHandler.set(clickHandler);
        cursor.set(clickHandler == null ? Cursor.DEFAULT : Cursor.HAND);
        saveVisible.set(false);
        saveAction.set(null);
        resetVisible.set(false);
        resetAction.set(null);
    }

    public void configureDetailMode(String saveText, EventHandler<ActionEvent> saveHandler,
            String resetText, EventHandler<ActionEvent> resetHandler) {
        mouseClickedHandler.set(null);
        cursor.set(Cursor.DEFAULT);
        if (saveHandler == null) {
            this.saveVisible.set(false);
            this.saveAction.set(null);
        } else {
            this.saveText.set(saveText);
            this.saveAction.set(saveHandler);
            this.saveVisible.set(true);
        }
        if (resetHandler == null) {
            this.resetVisible.set(false);
            this.resetAction.set(null);
        } else {
            this.resetText.set(resetText);
            this.resetAction.set(resetHandler);
            this.resetVisible.set(true);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
