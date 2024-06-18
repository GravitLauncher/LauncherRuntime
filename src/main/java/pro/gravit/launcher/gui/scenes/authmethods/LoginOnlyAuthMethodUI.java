package pro.gravit.launcher.gui.scenes.authmethods;

import javafx.scene.control.TextField;
import pro.gravit.launcher.gui.basic.ResourcePath;

import java.util.concurrent.CompletableFuture;

@ResourcePath("scenes/login/methods/loginonly.fxml")
public class LoginOnlyAuthMethodUI extends AuthMethodUI {
    private CompletableFuture<LoginAndPassword> future;
    private TextField loginField;
    @Override
    protected void doInit() {
        loginField = lookup("#login");
    }

    @Override
    public CompletableFuture<LoginAndPassword> auth() {
        future = new CompletableFuture<>();
        return future;
    }

    @Override
    public void onAuthButtonClicked() {
        future.complete(new LoginAndPassword(loginField.getText(), null));
    }
}
