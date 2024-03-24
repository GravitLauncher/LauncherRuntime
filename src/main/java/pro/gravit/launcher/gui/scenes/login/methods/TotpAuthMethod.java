package pro.gravit.launcher.gui.scenes.login.methods;

import javafx.scene.control.TextField;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.gui.impl.ContextHelper;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.launcher.base.request.auth.details.AuthTotpDetails;
import pro.gravit.launcher.base.request.auth.password.AuthTOTPPassword;

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
        overlay.maxLength = details.maxKeyLength;
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            ContextHelper.runInFxThreadStatic(() -> {
                accessor.showContent(overlay);
                future.complete(null);
            });
        } catch (Exception e) {
            accessor.errorHandle(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthTotpDetails details) {
        overlay.future = new CompletableFuture<>();
        String totp = overlay.getCode();
        if (totp != null && !totp.isEmpty()) {
            AuthTOTPPassword totpPassword = new AuthTOTPPassword();
            totpPassword.totp = totp;
            return CompletableFuture.completedFuture(new AuthFlow.LoginAndPasswordResult(null, totpPassword));
        }
        return overlay.future;
    }

    @Override
    public void onAuthClicked() {
        overlay.complete();
    }

    @Override
    public void onUserCancel() {
        overlay.future.completeExceptionally(TotpOverlay.USER_AUTH_CANCELED_EXCEPTION);
    }

    @Override
    public CompletableFuture<Void> hide() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    public static class TotpOverlay extends AbstractVisualComponent {
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
            totpField = LookupHelper.lookup(layout, "#totp");
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
            AuthTOTPPassword totpPassword = new AuthTOTPPassword();
            totpPassword.totp = getCode();
            future.complete(new AuthFlow.LoginAndPasswordResult(null, totpPassword));
        }

        public void requestFocus() {
            totpField.requestFocus();
        }

        public String getCode() {
            return totpField.getText();
        }

        @Override
        public void reset() {
            if(totpField == null) return;
            totpField.setText("");
        }

        @Override
        public void disable() {

        }

        @Override
        public void enable() {

        }
    }
}
