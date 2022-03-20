package pro.gravit.launcher.client.gui.service;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.password.*;
import pro.gravit.utils.helper.SecurityHelper;

import java.util.ArrayList;
import java.util.List;

public class AuthService {
    private final LauncherConfig config = Launcher.getConfig();
    private final JavaFXApplication application;

    public AuthService(JavaFXApplication application) {
        this.application = application;
    }

    public AuthRequest.AuthPasswordInterface makePassword(String plainPassword) {
        if (config.passwordEncryptKey != null) {
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

    public List<AuthRequest.AuthPasswordInterface> getListFromPassword(AuthRequest.AuthPasswordInterface password) {
        if(password instanceof Auth2FAPassword) {
            List<AuthRequest.AuthPasswordInterface> list = new ArrayList<>();
            Auth2FAPassword auth2FAPassword = (Auth2FAPassword) password;
            list.add(auth2FAPassword.firstPassword);
            list.add(auth2FAPassword.secondPassword);
            return list;
        } else if(password instanceof AuthMultiPassword) {
            return ((AuthMultiPassword) password).list;
        } else {
            List<AuthRequest.AuthPasswordInterface> list = new ArrayList<>(1);
            list.add(password);
            return list;
        }
    }

    public AuthRequest.AuthPasswordInterface getPasswordFromList(List<AuthRequest.AuthPasswordInterface> password) {
        if(password.size() == 1) {
            return password.get(0);
        }
        if(password.size() == 2) {
            Auth2FAPassword pass = new Auth2FAPassword();
            pass.firstPassword = password.get(0);
            pass.secondPassword = password.get(1);
            return pass;
        }
        AuthMultiPassword multi = new AuthMultiPassword();
        multi.list = password;
        return multi;
    }

    public AuthRequest makeAuthRequest(String login, AuthRequest.AuthPasswordInterface password, String authId) {
        return new AuthRequest(login, password, authId, false, application.isDebugMode() ? AuthRequest.ConnectTypes.API : AuthRequest.ConnectTypes.CLIENT);
    }

    private byte[] encryptAESPassword(String password) throws Exception {
        return SecurityHelper.encrypt(Launcher.getConfig().passwordEncryptKey, password);
    }
}
