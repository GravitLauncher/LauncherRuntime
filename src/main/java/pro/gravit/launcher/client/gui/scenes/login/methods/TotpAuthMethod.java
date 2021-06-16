package pro.gravit.launcher.client.gui.scenes.login.methods;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.request.auth.details.AuthTotpDetails;

import java.util.concurrent.CompletableFuture;

public class TotpAuthMethod extends AbstractAuthMethod<AuthTotpDetails> {
    @Override
    public void prepare() {

    }

    @Override
    public void reset() {

    }

    @Override
    public CompletableFuture<Void> show(AuthTotpDetails details) {
        return null;
    }

    @Override
    public CompletableFuture<LoginScene.LoginAndPasswordResult> auth(AuthTotpDetails details) {
        return null;
    }

    @Override
    public CompletableFuture<Void> hide() {
        return null;
    }
    public static class TotpOverlay extends AbstractOverlay {

        protected TotpOverlay(JavaFXApplication application) {
            super("scenes/login/login2fa.fxml", application);
        }

        @Override
        protected void doInit() {

        }

        @Override
        public void reset() {

        }
    }
}
