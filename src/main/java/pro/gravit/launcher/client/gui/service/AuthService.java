package pro.gravit.launcher.client.gui.service;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.request.auth.password.AuthAESPassword;
import pro.gravit.launcher.request.auth.password.AuthPlainPassword;
import pro.gravit.launcher.request.auth.password.AuthTOTPPassword;
import pro.gravit.utils.helper.SecurityHelper;

public class AuthService {
    private final LauncherConfig config = Launcher.getConfig();
    private final JavaFXApplication application;

    public AuthService(JavaFXApplication application) {
        this.application = application;
    }

    public AuthRequest.AuthPasswordInterface makePassword(String plainPassword) {
        if(config.passwordEncryptKey != null) {
            try {
                return new AuthAESPassword(encryptAESPassword(plainPassword));
            } catch (Exception ignored) {
            }
        }
        return new AuthPlainPassword(plainPassword);
    }
    public AuthRequest.AuthPasswordInterface make2FAPassword(AuthRequest.AuthPasswordInterface firstPassword, String totp) {
        Auth2FAPassword auth2FAPassword = new Auth2FAPassword();
        auth2FAPassword.firstPassword = firstPassword;
        auth2FAPassword.secondPassword = new AuthTOTPPassword();
        ((AuthTOTPPassword) auth2FAPassword.secondPassword).totp = totp;
        return auth2FAPassword;
    }
    public AuthRequest makeAuthRequest(String login, AuthRequest.AuthPasswordInterface password, String authId) {
        return new AuthRequest(login, password, authId, true, application.isDebugMode() ? AuthRequest.ConnectTypes.API : AuthRequest.ConnectTypes.CLIENT);
    }
    private byte[] encryptAESPassword(String password) throws Exception {
        return SecurityHelper.encrypt(Launcher.getConfig().passwordEncryptKey, password);
    }
}
