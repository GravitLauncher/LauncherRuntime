package pro.gravit.launcher.gui.core.service;

import javafx.beans.value.ObservableValue;
import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.gui.core.JavaFXApplication;

public class AuthService {
    private final JavaFXApplication application;
    private final javafx.beans.property.ReadOnlyObjectWrapper<SelfUser> user = new javafx.beans.property.ReadOnlyObjectWrapper<>();
    public javafx.beans.property.ReadOnlyObjectProperty<SelfUser> userProperty() { return user.getReadOnlyProperty(); }
    public final ObservableValue<String> username = user.map(SelfUser::getUsername).orElse("Player");
    private AuthMethod authAvailability;

    public AuthService(JavaFXApplication application) {
        this.application = application;
    }

    public void setUser(SelfUser user) {
        this.user.set(user);
    }

    public void setAuthAvailability(AuthMethod info) {
        this.authAvailability = info;
    }

    public AuthMethod getAuthAvailability() {
        return authAvailability;
    }

    public boolean isFeatureAvailable(String name) {
        return authAvailability != null && authAvailability.getFeatures() != null && authAvailability.getFeatures().contains(name);
    }

    public String getUsername() {
        return username.getValue();
    }

    public String getMainRole() {
        return "";
    }

    public boolean checkPermission(String name) {
        if (user.get() == null || user.get().getPermissions() == null) {
            return false;
        }
        return user.get().getPermissions().hasPerm(name);
    }

    public boolean checkDebugPermission(String name) {
        return application.isDebugMode() || (!application.guiModuleConfig.disableDebugPermissions &&
                checkPermission("launcher.debug."+name));
    }

    public void exit() {
        user.set(null);

    }
}
