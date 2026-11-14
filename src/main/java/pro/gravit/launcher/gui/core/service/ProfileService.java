package pro.gravit.launcher.gui.core.service;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;
import java.util.List;

public final class ProfileService {
    private final ObservableList<ProfileFeatureAPI.ClientProfile> profiles = FXCollections.observableArrayList();
    private final ObservableList<ProfileFeatureAPI.ClientProfile> readOnlyProfiles = FXCollections.unmodifiableObservableList(profiles);
    private final ReadOnlyObjectWrapper<ProfileFeatureAPI.ClientProfile> selected = new ReadOnlyObjectWrapper<>();

    public ProfileService(JavaFXApplication application) { }
    public ObservableList<ProfileFeatureAPI.ClientProfile> getProfiles() { return readOnlyProfiles; }
    public ReadOnlyObjectProperty<ProfileFeatureAPI.ClientProfile> currentProfileProperty() { return selected.getReadOnlyProperty(); }
    public ProfileFeatureAPI.ClientProfile getCurrentProfile() { return selected.get(); }
    public void setCurrentProfile(ProfileFeatureAPI.ClientProfile profile) { FxDispatcher.checkThread(); selected.set(profile); }
    public void setProfiles(List<ProfileFeatureAPI.ClientProfile> values) {
        FxDispatcher.checkThread();
        var old = selected.get();
        profiles.setAll(values == null ? List.of() : values);
        if (old != null) selected.set(profiles.stream().filter(p -> p.getUUID().equals(old.getUUID())).findFirst().orElse(null));
    }
    public void clear() { setProfiles(List.of()); selected.set(null); }
}
