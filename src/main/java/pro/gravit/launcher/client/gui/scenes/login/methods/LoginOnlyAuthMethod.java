package pro.gravit.launcher.client.gui.scenes.login.methods;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.login.LoginAuthButtonComponent;
import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.details.AuthLoginOnlyDetails;
import pro.gravit.launcher.request.auth.details.AuthPasswordDetails;

import java.util.concurrent.CompletableFuture;

public class LoginOnlyAuthMethod extends AbstractAuthMethod<AuthLoginOnlyDetails> {
    private final LoginOnlyOverlay overlay;
    private final JavaFXApplication application;
    private final LoginScene.LoginSceneAccessor accessor;

    public LoginOnlyAuthMethod(LoginScene.LoginSceneAccessor accessor) {
        this.accessor = accessor;
        this.application = accessor.getApplication();
        this.overlay = application.gui.registerOverlay(LoginOnlyOverlay.class);
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
    public CompletableFuture<Void> show(AuthLoginOnlyDetails details) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            accessor.showOverlay(overlay, (e) -> future.complete(null));
        } catch (Exception e) {
            accessor.errorHandle(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<LoginScene.LoginAndPasswordResult> auth(AuthLoginOnlyDetails details) {
        overlay.future = new CompletableFuture<>();
        String login = overlay.login.getText();
        if (login != null && !login.isEmpty()) {
            return CompletableFuture.completedFuture(new LoginScene.LoginAndPasswordResult(login, null));
        }
        return overlay.future;
    }

    @Override
    public CompletableFuture<Void> hide() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        accessor.hideOverlay(0, (e) -> future.complete(null));
        return future;
    }

    @Override
    public boolean isSavable() {
        return true;
    }

    public static class LoginOnlyOverlay extends AbstractOverlay {
        private static final UserAuthCanceledException USER_AUTH_CANCELED_EXCEPTION = new UserAuthCanceledException();
        private TextField login;
        private LoginAuthButtonComponent authButton;
        private LoginScene.LoginSceneAccessor accessor;
        private CompletableFuture<LoginScene.LoginAndPasswordResult> future;

        public LoginOnlyOverlay(JavaFXApplication application) {
            super("scenes/login/loginonly.fxml", application);
        }

        @Override
        public String getName() {
            return "loginonly";
        }

        @Override
        protected void doInit() {
            login = LookupHelper.lookup(layout, "#login");
            authButton = new LoginAuthButtonComponent(LookupHelper.lookup(layout, "#authButtonBlock"), application, e -> {
                String rawLogin = login.getText();
                future.complete(new LoginScene.LoginAndPasswordResult(rawLogin, null));
            });
            LookupHelper.<ButtonBase>lookup(layout, "#header", "#controls", "#exit").setOnAction(e -> {
                accessor.hideOverlay(0, null);
                future.completeExceptionally(USER_AUTH_CANCELED_EXCEPTION);
            });
            login.textProperty().addListener(l -> {
                authButton.setActive(!login.getText().isEmpty());
            });
            if (application.runtimeSettings.login != null) {
                login.setText(application.runtimeSettings.login);
                authButton.setActive(true);
            } else {
                authButton.setActive(false);
            }
        
            if (application.guiModuleConfig.createAccountURL != null)
                LookupHelper.<Text>lookup(layout, "#createAccount").setOnMouseClicked((e) ->
                        application.openURL(application.guiModuleConfig.createAccountURL));
            if (application.guiModuleConfig.forgotPassURL != null)
                LookupHelper.<Text>lookup(layout, "#forgotPass").setOnMouseClicked((e) ->
                        application.openURL(application.guiModuleConfig.forgotPassURL));
        }


        @Override
        public void reset() {
            login.setText("");
        }
    }
}
