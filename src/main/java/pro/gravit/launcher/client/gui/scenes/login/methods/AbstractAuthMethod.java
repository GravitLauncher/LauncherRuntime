package pro.gravit.launcher.client.gui.scenes.login.methods;

import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractAuthMethod<T extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> {
    public abstract void prepare();

    public abstract void reset();

    public abstract CompletableFuture<Void> show(T details);

    public abstract CompletableFuture<LoginScene.LoginAndPasswordResult> auth(T details);

    public abstract CompletableFuture<Void> hide();

    public static class UserAuthCanceledException extends RuntimeException {
        public UserAuthCanceledException() {
        }

        public UserAuthCanceledException(String message) {
            super(message);
        }

        public UserAuthCanceledException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
