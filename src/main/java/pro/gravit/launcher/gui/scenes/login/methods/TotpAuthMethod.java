package pro.gravit.launcher.gui.scenes.login.methods;

import javafx.scene.control.TextField;
import pro.gravit.launcher.core.api.method.details.AuthTotpDetails;
import pro.gravit.launcher.core.api.method.password.AuthTotpPassword;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxComponent;
import pro.gravit.launcher.gui.core.impl.ContextHelper;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;

import java.util.concurrent.CompletableFuture;

public class TotpAuthMethod extends AbstractAuthMethod<AuthTotpDetails> {
    private final TotpOverlay overlay;
    private final JavaFXApplication application;
    private final LoginScene.LoginSceneAccessor accessor;

    public TotpAuthMethod(LoginScene.LoginSceneAccessor accessor) {
        this.accessor = accessor;
        this.application = accessor.getApplication();
        this.overlay = new TotpOverlay(application);
        this.overlay.accessor = accessor;
    }

    @Override
    public void prepare() {

    }

    @Override
    public void reset() {
        overlay.reset();
    }

    @Override
    public CompletableFuture<Void> show(AuthTotpDetails details) {
        overlay.maxLength = 6;
        return ContextHelper.runInFxThreadStatic(() -> accessor.showContent(overlay));
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthTotpDetails details) {
        overlay.future = new CompletableFuture<>();
        accessor.runInFxThread(() -> overlay.totpField.clear());
        return overlay.future;
    }

    @Override
    public void onAuthClicked() {
        overlay.complete();
    }

    @Override
    public void onUserCancel() {
        if (overlay.future != null) overlay.future.completeExceptionally(TotpOverlay.USER_AUTH_CANCELED_EXCEPTION);
    }

    @Override
    public CompletableFuture<Void> hide() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    public static class TotpOverlay extends FxComponent {
        private static final UserAuthCanceledException USER_AUTH_CANCELED_EXCEPTION = new UserAuthCanceledException();
        private TextField totpField;
        private CompletableFuture<AuthFlow.LoginAndPasswordResult> future;
        private LoginScene.LoginSceneAccessor accessor;
        private int maxLength;

        public TotpOverlay(JavaFXApplication application) {
            super("scenes/login/methods/totp.fxml", application);
        }

        @Override
        public String getName() {
            return "totp";
        }

        @Override
        protected void doInit() {
            totpField = LookupHelper.lookup(layout, TextField.class, "#totp");
            totpField.textProperty().addListener((obj, oldValue, value) -> {
                if(value != null && value.length() == maxLength) {
                    complete();
                }
            });
            totpField.setOnAction((e) -> {
                if(totpField.getText() != null && !totpField.getText().isEmpty()) {
                    complete();
                }
            });
        }

        @Override
        protected void doPostInit() {

        }

        public void complete() {
            String code = getCode();
            if (code == null || code.isEmpty()) return;
            if (future != null) future.complete(new AuthFlow.LoginAndPasswordResult(null, new AuthTotpPassword(code)));
        }

        public void requestFocus() {
            totpField.requestFocus();
        }

        public String getCode() {
            return totpField.getText();
        }

        @Override
        public void reset() {
            if (totpField != null) {
                accessor.runInFxThread(() -> totpField.clear());
            }
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new UserAuthCanceledException());
            }
            future = null;
        }

        @Override
        public void disable() {

        }

        @Override
        public void enable() {

        }
    }
}
