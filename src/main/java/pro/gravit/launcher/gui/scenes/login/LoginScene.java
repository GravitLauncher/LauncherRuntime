package pro.gravit.launcher.gui.scenes.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import javafx.util.StringConverter;
import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.impl.UIComponent;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoginScene extends FxScene {

    private static final Logger logger =
            LoggerFactory.getLogger(LoginScene.class);

    private List<AuthMethod> auth; //TODO: FIX? Field is assigned but never accessed.
    //private CheckBox savePasswordCheckBox;
    private CheckBox autoenter;
    private Pane content;
    private UIComponent contentComponent;
    private final java.util.Set<UIComponent> ownedContent = new java.util.HashSet<>();
    private AuthButton authButton;
    private ComboBox<AuthMethod> authList;
    private AuthMethod authAvailability;
    private final AuthFlow authFlow;

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
        LoginSceneAccessor accessor = new LoginSceneAccessor();
        this.authFlow = new AuthFlow(accessor, this::onSuccessLogin);
    }

    @Override
    public void doInit() {
        LookupHelper.lookup(header, ButtonBase.class, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.globalSettingsScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        authButton = use(layout, AuthButton::new);
        authButton.setOnAction((e) -> contextHelper.runCallback(authFlow::loginWithGui));
        autoenter = LookupHelper.lookup(layout, CheckBox.class, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());

        content = LookupHelper.lookup(layout, Pane.class, "#content");
        if (application.guiModuleConfig.createAccountURL != null) {
            LookupHelper.lookup(header, ButtonBase.class, "#createAccount")
                        .setOnAction((e) -> application.openURL(application.guiModuleConfig.createAccountURL));
        }
        if (application.guiModuleConfig.forgotPassURL != null) {
            LookupHelper.lookup(header, ButtonBase.class, "#forgotPass")
                        .setOnAction((e) -> application.openURL(application.guiModuleConfig.forgotPassURL));
        }
        authList = LookupHelper.lookup(layout, ComboBox.class, "#authList");
        authList.setConverter(new AuthAvailabilityStringConverter());
        authList.setOnAction((e) -> changeAuthAvailability(authList.getSelectionModel().getSelectedItem()));
        authFlow.prepare();
    }

    @Override
    protected void doPostInit() {
        getAvailabilityAuth();
    }

    private void clearOAuthToken() {
        // Удаляем backend settings с токеном
        LauncherBackendAPIHolder.getApi().getUserSettings("backend", (a) -> null);
        // Конкретный способ очистки зависит от реализации LauncherBackendAPI
    }
    private void getAvailabilityAuth() {
        processing(application.backendCallbackService.initDataCallback,
                   application.getTranslation("runtime.overlay.processing.text.launcher"),
                   (initData) -> contextHelper.runInFxThread(() -> {
                       this.auth = initData.methods();
                       authList.setVisible(auth.size() != 1);
                       authList.setManaged(auth.size() != 1);
                       for (var authAvailability : auth) {
                           if (!authAvailability.isVisible()) {
                               continue;
                           }
                           if(authAvailability.isVisible()) {
                               addAuthAvailability(authAvailability);
                           }
                       }
                       this.authAvailability = LauncherBackendAPIHolder.getApi().getAuthMethod();
                       if(this.authAvailability != null) {
                           changeAuthAvailability(this.authAvailability);
                       }
                       if (this.authAvailability == null && !auth.isEmpty()) {
                           changeAuthAvailability(auth.get(0));
                       }
                       runAutoAuth();
                   }), (e) -> {
                        errorHandle(e);
                        contextHelper.runAfterTimeout(Duration.seconds(2), () -> {
                            Platform.exit();
                            return null;
                        });
                });
    }
    private void runAutoAuth() {
        boolean shouldAutoAuth = application.guiModuleConfig.autoAuth
                || application.runtimeSettings.autoAuth;
        if (!shouldAutoAuth) return;

        authFlow.tryAutoLogin().thenAccept(success -> {
            if (!success) contextHelper.runInFxThread(authFlow::loginWithGui);
        });
    }

    public void changeAuthAvailability(AuthMethod authAvailability) {
        if (authAvailability == null) return;
        if (this.authAvailability == authAvailability && application.authService.getAuthAvailability() != null) return;
        LauncherBackendAPIHolder.getApi().selectAuthMethod(authAvailability);
        this.authAvailability = authAvailability;
        this.application.authService.setAuthAvailability(authAvailability);
        authFlow.init(authAvailability);
        this.authList.selectionModelProperty().get().select(authAvailability);
        logger.trace("Selected auth: {}", authAvailability.getName());
    }

    public void addAuthAvailability(AuthMethod authAvailability) {
        authList.getItems().add(authAvailability);
        logger.trace("Added {}: {}", authAvailability.getName(), authAvailability.getDisplayName());
    }

    public <T> void processing(CompletableFuture<T> request, String text, Consumer<T> onSuccess,
            Consumer<String> onError) {
        processRequest(text, request, onSuccess, onError == null ? null :
                               (thr) -> {
                                   Throwable cause = thr;
                                   while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException)) cause = cause.getCause();
                                   onError.accept(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                               },
                       null);
    }


    @Override
    public void errorHandle(Throwable e) {
        super.errorHandle(e);
        contextHelper.runInFxThread(() -> authButton.setState(AuthButton.AuthButtonState.ERROR));
    }

    @Override
    public void reset() {
        authFlow.reset();
    }

    @Override public void onHide() {
        if (authFlow.isLoginStarted) authFlow.reset();
        super.onHide();
    }
    @Override public void dispose() {
        authFlow.reset();
        super.dispose();
    }

    @Override
    public String getName() {
        return "login";
    }

    public void onSuccessLogin(AuthFlow.SuccessAuth successAuth) {
        var user = successAuth.user();
        application.authService.setUser(user);

        if (user != null) application.skinManager.load(user);
        contextHelper.runInFxThread(() -> {
            if(application.gui.welcomeOverlay.isInit()) {
                application.gui.welcomeOverlay.reset();
            }
            showOverlay(application.gui.welcomeOverlay,
                                                      (e) -> application.gui.welcomeOverlay.hide(2000,
                                                                                                 (f) -> onGetProfiles()));});
    }

    public void onGetProfiles() {
        processing(LauncherBackendAPIHolder.getApi().fetchProfiles(), application.getTranslation("runtime.overlay.processing.text.profiles"),
                   (profiles) -> contextHelper.runInFxThread(() -> {
                           if (application.getCurrentScene() instanceof LoginScene loginScene) {
                               loginScene.authFlow.isLoginStarted = false;
                           }
                           application.profileService.setProfiles(profiles);
                           application.setMainScene(application.gui.serverMenuScene);
                   }), null);
    }

    public AuthFlow getAuthFlow() {
        return authFlow;
    }

    private static class AuthAvailabilityStringConverter extends StringConverter<AuthMethod> {
        @Override
        public String toString(AuthMethod object) {
            return object == null ? "null" : object.getDisplayName();
        }

        @Override
        public AuthMethod fromString(String string) {
            return null;
        }
    }

    public class LoginSceneAccessor extends SceneAccessor {

        public void showContent(UIComponent component) throws Exception {
            component.init();
            component.postInit();
            if (ownedContent.add(component)) scope.add(component::dispose);
            if (contentComponent != null) {
                content.getChildren().clear();
            }
            contentComponent = component;
            content.getChildren().add(component.getLayout());
        }

        public AuthButton getAuthButton() {
            return authButton;
        }

        public void setState(AuthButton.AuthButtonState state) {
            if (authButton != null) authButton.setState(state);
        }

        public boolean isEmptyContent() {
            return content == null || content.getChildren().isEmpty();
        }

        public void clearContent() {
            content.getChildren().clear();
        }

        public <T> void processing(CompletableFuture<T> request, String text, Consumer<T> onSuccess,
                Consumer<String> onError) {
            LoginScene.this.processing(request, text, onSuccess, onError);
        }
    }


}
