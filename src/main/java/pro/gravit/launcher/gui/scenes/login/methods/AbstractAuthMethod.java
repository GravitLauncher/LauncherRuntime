package pro.gravit.launcher.gui.scenes.login.methods;

import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.base.events.request.GetAvailabilityAuthRequestEvent;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractAuthMethod<T extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> {
    public abstract void prepare();

    public abstract void reset();

    public abstract CompletableFuture<Void> show(T details);

    public abstract CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(T details);

    public abstract void onAuthClicked();

    public abstract void onUserCancel();

    public abstract CompletableFuture<Void> hide();

    public abstract boolean isOverlay();

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
