package pro.gravit.launcher.gui.scenes.authmethods;

import javafx.scene.control.TextField;
import pro.gravit.launcher.core.api.method.password.AuthTotpPassword;
import pro.gravit.launcher.gui.basic.ResourcePath;

import java.util.concurrent.CompletableFuture;

@ResourcePath("scenes/login/methods/totp.fxml")
public class TotpAuthMethodUI extends AuthMethodUI {
    private CompletableFuture<LoginAndPassword> future;
    private TextField totpField;

    @Override
    protected void doInit() {
        totpField = lookup("#totp");
    }

    @Override
    public CompletableFuture<LoginAndPassword> auth() {
        future = new CompletableFuture<>();
        return future;
    }

    @Override
    public void onAuthButtonClicked() {
        future.complete(new LoginAndPassword(null, new AuthTotpPassword(totpField.getText())));
    }
}
