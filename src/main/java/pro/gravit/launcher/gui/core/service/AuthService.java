package pro.gravit.launcher.gui.core.service;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.LauncherConfig;
import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.api.method.AuthMethodPassword;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.utils.helper.SecurityHelper;

public class AuthService {
    private final LauncherConfig config = Launcher.getConfig();
    private final JavaFXApplication application;
    public final SimpleObjectProperty<SelfUser> user = new SimpleObjectProperty<>();
    public final ObservableValue<String> username = user.map(user -> user == null ? "Player" : user.getUsername());
    private AuthMethod authAvailability;

    public AuthService(JavaFXApplication application) {
        this.application = application;
    }

    private byte[] encryptAESPassword(String password) throws Exception {
        return SecurityHelper.encrypt(Launcher.getConfig().passwordEncryptKey, password);
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
        return authAvailability.getFeatures() != null && authAvailability.getFeatures().contains(name);
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
        //.profile = null;
    }
}
