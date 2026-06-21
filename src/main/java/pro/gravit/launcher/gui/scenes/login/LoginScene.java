package pro.gravit.launcher.gui.scenes.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;
import pro.gravit.launcher.base.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.api.model.Texture;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.core.backend.UserSettings;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.impl.UIComponent;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.runtime.backend.BackendSettings;

import java.net.URI;
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
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.globalSettingsScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        authButton = use(layout, AuthButton::new);
        authButton.setOnAction((e) -> contextHelper.runCallback(authFlow::loginWithGui));
        //savePasswordCheckBox = LookupHelper.lookup(layout, "#savePassword");
        autoenter = LookupHelper.lookup(layout, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());

        // Показываем галочку сохранения если есть логин или OAuth токен
        /*savePasswordCheckBox.setSelected(application.runtimeSettings.login != null || hasOAuthToken());
        savePasswordCheckBox.setOnAction((event) -> {
            if (!savePasswordCheckBox.isSelected()) {
                application.runtimeSettings.login = null;
                application.runtimeSettings.password = null;
                application.runtimeSettings.lastAuth = null;
                // Вызываем userExit только если пользователь реально авторизован
                if (LauncherBackendAPIHolder.getApi().getSelfUser() != null) {
                    LauncherBackendAPIHolder.getApi().userExit();
                } else {
                    // Просто очищаем токен локально без запроса к серверу
                    UserSettings settings = LauncherBackendAPIHolder.getApi().getUserSettings("backend", (a) -> null);
                    if (settings instanceof BackendSettings backendSettings) {
                        backendSettings.auth = null;
                    }
                }
            }
        });*/

        content = LookupHelper.lookup(layout, "#content");
        if (application.guiModuleConfig.createAccountURL != null) {
            LookupHelper.<Text>lookup(header, "#createAccount")
                        .setOnMouseClicked((e) -> application.openURL(application.guiModuleConfig.createAccountURL));
        }
        if (application.guiModuleConfig.forgotPassURL != null) {
            LookupHelper.<Text>lookup(header, "#forgotPass")
                        .setOnMouseClicked((e) -> application.openURL(application.guiModuleConfig.forgotPassURL));
        }
        authList = LookupHelper.lookup(layout, "#authList");
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

        boolean hasOAuthToken = hasOAuthToken();

        if (hasOAuthToken) {
            authFlow.tryAutoLogin().thenAccept(success -> {
                if (!success) {
                    contextHelper.runInFxThread(authFlow::loginWithGui);
                }
            });
        }
    }

    private boolean hasOAuthToken() {
        UserSettings settings = LauncherBackendAPIHolder.getApi().getUserSettings("backend", (a) -> null);
        if (settings instanceof BackendSettings backendSettings) {
            return backendSettings.auth != null && backendSettings.auth.accessToken != null;
        }
        return false;
    }

    public void changeAuthAvailability(AuthMethod authAvailability) {
        boolean isChanged = this.authAvailability != authAvailability; //TODO: FIX
        LauncherBackendAPIHolder.getApi().selectAuthMethod(authAvailability);
        this.authAvailability = authAvailability;
        this.application.authService.setAuthAvailability(authAvailability);
        this.authList.selectionModelProperty().get().select(authAvailability);
        authFlow.init(authAvailability);
        logger.trace("Selected auth: {}", authAvailability.getName());
    }

    public void addAuthAvailability(AuthMethod authAvailability) {
        authList.getItems().add(authAvailability);
        logger.trace("Added {}: {}", authAvailability.getName(), authAvailability.getDisplayName());
    }

    public <T> void processing(CompletableFuture<T> request, String text, Consumer<T> onSuccess,
            Consumer<String> onError) {
        processRequest(text, request, onSuccess, onError == null ? null :
                               (thr) -> onError.accept(thr.getCause().getMessage()),
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

    @Override
    public String getName() {
        return "login";
    }

    public void onSuccessLogin(AuthFlow.SuccessAuth successAuth) {
        var user = successAuth.user();
        application.authService.setUser(user);

        if (user != null
                && user.getAssets() != null) {
            try {
                Texture skin = user.getAssets().get("SKIN");
                Texture avatar = user.getAssets().get("AVATAR");
                if(skin != null || avatar != null) {
                    application.skinManager.addSkinWithAvatar(user.getUsername(),
                                                              skin != null ? new URI(skin.getUrl()) : null,
                                                              avatar != null ? new URI(avatar.getUrl()) : null);
                    application.skinManager.getSkin(user.getUsername()); //Cache skin
                }
            } catch (Exception e) {
                logger.error("", e);
            }
        }
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
                   (profiles) -> {/*
                       application.profilesService.setProfilesResult(profiles);
                       application.runtimeSettings.profiles = profiles.profiles;*/
                       contextHelper.runInFxThread(() -> {
                           /*
                           if (application.gui.optionsScene != null) {
                               try {
                                   application.profilesService.loadAll();
                               } catch (Throwable ex) {
                                   errorHandle(ex);
                               }
                           }*/
                           if (application.getCurrentScene() instanceof LoginScene loginScene) {
                               loginScene.authFlow.isLoginStarted = false;
                           }
                           application.profileService.setProfiles(profiles);
                           application.setMainScene(application.gui.serverMenuScene);
                       });
                   }, null);
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
        public GetAvailabilityAuthRequestEvent.AuthAvailability fromString(String string) {
            return null;
        }
    }

    public class LoginSceneAccessor extends SceneAccessor {

        public void showContent(UIComponent component) throws Exception {
            component.init();
            component.postInit();
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
            authButton.setState(state);
        }

        public boolean isEmptyContent() {
            return content.getChildren().isEmpty();
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