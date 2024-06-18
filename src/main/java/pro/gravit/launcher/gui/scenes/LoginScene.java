package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.base.request.auth.password.AuthMultiPassword;
import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.api.method.AuthMethodDetails;
import pro.gravit.launcher.core.api.method.AuthMethodPassword;
import pro.gravit.launcher.core.api.method.details.AuthLoginOnlyDetails;
import pro.gravit.launcher.core.api.method.details.AuthPasswordDetails;
import pro.gravit.launcher.core.api.method.password.AuthChainPassword;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FXApplication;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.scenes.authmethods.AuthMethodUI;
import pro.gravit.launcher.gui.scenes.authmethods.LoginOnlyAuthMethodUI;
import pro.gravit.launcher.gui.scenes.authmethods.PasswordAuthMethodUI;
import pro.gravit.utils.helper.LogHelper;

import java.util.*;

@ResourcePath("scenes/login/login.fxml")
public class LoginScene extends FxScene {
    private Map<Class<? extends AuthMethodDetails>, AuthMethodUI> authMethodUiMap;
    private Button authButton;
    private Pane content;
    private ComboBox<AuthMethod> authList;
    // Auth
    private AuthMethodUI authMethodUI;
    private AuthMethod authMethod;
    private Queue<AuthMethodDetails> detailsQueue;
    private String selectedLogin;
    private List<AuthMethodPassword> selectedPassword;
    //
    @Override
    protected void doInit() {
        super.doInit();
        authMethodUiMap = new HashMap<>();
        authMethodUiMap.put(AuthLoginOnlyDetails.class, FXApplication.getInstance().register(new LoginOnlyAuthMethodUI()));
        authMethodUiMap.put(AuthPasswordDetails.class, FXApplication.getInstance().register(new PasswordAuthMethodUI()));
        authButton = lookup("#authButton");
        authButton.setOnAction((e) -> onAuthButtonClick());
        content = lookup("#content");
        authList = lookup("#authList");
        authList.setConverter(new AuthMethodConverter());
        authList.setOnAction((e) -> {
            updateAuthMethod(authList.getValue());
        });
        LauncherBackendAPIHolder.getApi().init()
                                .thenAcceptAsync(this::onInitialized, FxThreadExecutor.getInstance())
                                .exceptionally(this::errorHandleFuture);
    }

    protected void onAuthButtonClick() {
        if(authMethodUI == null) {
            updateAuthMethodUI();
            return;
        }
        authMethodUI.onAuthButtonClicked();
    }

    protected void updateAuthMethodUI() {
        detailsQueue = new ArrayDeque<>(authMethod.getDetails());
        selectedLogin = null;
        selectedPassword = new ArrayList<>();
        nextAuth();
    }

    protected void nextAuth() {
        var details = detailsQueue.poll();
        if(details == null) {
            tryAuthorize();
            return;
        }
        var method = authMethodUiMap.get(details.getClass());
        content.getChildren().clear();
        authMethodUI = method;
        inject(content, authMethodUI);
        authMethodUI.auth()
                    .thenAcceptAsync(this::processAuthStage, FxThreadExecutor.getInstance())
                    .exceptionally(this::errorHandleFuture);
    }

    protected void processAuthStage(AuthMethodUI.LoginAndPassword loginAndPassword) {
        if(loginAndPassword.login() != null) {
            selectedLogin = loginAndPassword.login();
        }
        if(loginAndPassword.password() != null) {
            selectedPassword.add(loginAndPassword.password());
        }
        nextAuth();
    }

    protected void tryAuthorize() {
        AuthMethodPassword password;
        if(selectedPassword.size() == 1) {
            password = selectedPassword.get(0);
        } else {
            password = new AuthChainPassword(selectedPassword);
        }
        LauncherBackendAPIHolder.getApi().authorize(selectedLogin, password).exceptionally(this::errorHandleFuture);
    }

    protected void onInitialized(LauncherBackendAPI.LauncherInitData data) {
        LogHelper.debug("Initialized");
        updateAuthMethods(data.methods());
    }

    protected void updateAuthMethods(List<AuthMethod> methods) {
        AuthMethod selectedMethod = null;
        for(var method : methods) {
            if(!method.isVisible()) {
                continue;
            }
            authList.getItems().add(method);
            if(selectedMethod == null) {
                selectedMethod = method;
            }
        }
        if(selectedMethod != null) {
            updateAuthMethod(selectedMethod);
        }
    }

    public void updateAuthMethod(AuthMethod authMethod) {
        LauncherBackendAPIHolder.getApi().selectAuthMethod(authMethod);
        authList.getSelectionModel().select(authMethod);
        this.authMethod = authMethod;
    }

    private static class AuthMethodConverter extends StringConverter<AuthMethod> {

        @Override
        public String toString(AuthMethod authMethod) {
            return authMethod == null ? "Unknown" : authMethod.getDisplayName();
        }

        @Override
        public AuthMethod fromString(String s) {
            return null;
        }
    }
}
