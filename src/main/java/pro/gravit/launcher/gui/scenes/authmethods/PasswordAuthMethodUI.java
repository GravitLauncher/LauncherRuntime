package pro.gravit.launcher.gui.scenes.authmethods;

import javafx.scene.control.TextField;
import pro.gravit.launcher.core.api.method.password.AuthPlainPassword;
import pro.gravit.launcher.gui.basic.ResourcePath;

import java.util.concurrent.CompletableFuture;

@ResourcePath("scenes/login/methods/loginpassword.fxml")
public class PasswordAuthMethodUI extends AuthMethodUI {
    private CompletableFuture<LoginAndPassword> future;
    private TextField loginField;
    private TextField passwordField;
    @Override
    protected void doInit() {
        loginField = lookup("#login");
        passwordField = lookup("#password");
    }

    @Override
    public CompletableFuture<LoginAndPassword> auth() {
        future = new CompletableFuture<>();
        return future;
    }

    @Override
    public void onAuthButtonClicked() {
        future.complete(new LoginAndPassword(loginField.getText(), new AuthPlainPassword(passwordField.getText())));
    }
}
