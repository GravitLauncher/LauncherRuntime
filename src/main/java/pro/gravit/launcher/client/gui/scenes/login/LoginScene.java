package pro.gravit.launcher.client.gui.scenes.login;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import pro.gravit.launcher.client.StdJavaRuntimeProvider;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.login.methods.*;
import pro.gravit.launcher.runtime.LauncherEngine;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.runtime.utils.LauncherUpdater;
import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.base.profiles.Texture;
import pro.gravit.launcher.base.request.Request;
import pro.gravit.launcher.base.request.RequestException;
import pro.gravit.launcher.base.request.WebSocketEvent;
import pro.gravit.launcher.base.request.auth.AuthRequest;
import pro.gravit.launcher.base.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.base.request.auth.RefreshTokenRequest;
import pro.gravit.launcher.base.request.auth.details.AuthLoginOnlyDetails;
import pro.gravit.launcher.base.request.auth.details.AuthPasswordDetails;
import pro.gravit.launcher.base.request.auth.details.AuthTotpDetails;
import pro.gravit.launcher.base.request.auth.details.AuthWebViewDetails;
import pro.gravit.launcher.base.request.auth.password.*;
import pro.gravit.launcher.base.request.update.LauncherRequest;
import pro.gravit.launcher.base.request.update.ProfilesRequest;
import pro.gravit.utils.helper.LogHelper;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoginScene extends AbstractScene {
    public Map<Class<? extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>, AbstractAuthMethod<? extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>> authMethods = new HashMap<>(
            8);
    public boolean isLoginStarted;
    private List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth; //TODO: FIX? Field is assigned but never accessed.
    private CheckBox savePasswordCheckBox;
    private CheckBox autoenter;
    private Pane content;
    private AbstractVisualComponent contentComponent;
    private LoginAuthButtonComponent authButton;
    private ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList;
    private GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability;
    private final AuthFlow authFlow = new AuthFlow();

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
        LoginSceneAccessor accessor = new LoginSceneAccessor();
        authMethods.put(AuthPasswordDetails.class, new LoginAndPasswordAuthMethod(accessor));
        authMethods.put(AuthWebViewDetails.class, new WebAuthMethod(accessor));
        authMethods.put(AuthTotpDetails.class, new TotpAuthMethod(accessor));
        authMethods.put(AuthLoginOnlyDetails.class, new LoginOnlyAuthMethod(accessor));
    }

    @Override
    public void doInit() {
        authButton = new LoginAuthButtonComponent(LookupHelper.lookup(layout, "#authButton"), application,
                                                  (e) -> contextHelper.runCallback(this::loginWithGui));
        savePasswordCheckBox = LookupHelper.lookup(layout, "#savePassword");
        if (application.runtimeSettings.password != null || application.runtimeSettings.oauthAccessToken != null) {
            LookupHelper.<CheckBox>lookup(layout, "#savePassword").setSelected(true);
        }
        autoenter = LookupHelper.lookup(layout, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());
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
        authMethods.forEach((k, v) -> v.prepare());
        // Verify Launcher
    }

    @Override
    protected void doPostInit() {

        if (!application.isDebugMode()) {
            // we would like to wait till launcher request success before start availability auth.
            // otherwise it will try to access same vars same time, and this causes a lot of multi-thread based errors
            // launcherRequest().finally(getAvailabilityAuth().finally(postInit()))
            launcherRequest();
        } else {
            getAvailabilityAuth();
        }
    }

    private void launcherRequest() {
        LauncherRequest launcherRequest = new LauncherRequest();
        processRequest(application.getTranslation("runtime.overlay.processing.text.launcher"), launcherRequest,
                       (result) -> {
                           if (result.needUpdate) {
                               try {
                                   LogHelper.debug("Start update processing");
                                   disable();
                                   StdJavaRuntimeProvider.updatePath = LauncherUpdater.prepareUpdate(
                                           new URL(result.url));
                                   LogHelper.debug("Exit with Platform.exit");
                                   Platform.exit();
                                   return;
                               } catch (Throwable e) {
                                   contextHelper.runInFxThread(() -> errorHandle(e));
                                   try {
                                       Thread.sleep(1500);
                                       LauncherEngine.modulesManager.invokeEvent(new ClientExitPhase(0));
                                       Platform.exit();
                                   } catch (Throwable ex) {
                                       LauncherEngine.exitLauncher(0);
                                   }
                               }
                           }
                           LogHelper.dev("Launcher update processed");
                           getAvailabilityAuth();
                       }, (event) -> LauncherEngine.exitLauncher(0));
    }

    private void getAvailabilityAuth() {
        GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
        processing(getAvailabilityAuthRequest,
                   application.getTranslation("runtime.overlay.processing.text.authAvailability"),
                   (auth) -> contextHelper.runInFxThread(() -> {
                       this.auth = auth.list;
                       authList.setVisible(auth.list.size() != 1);
                       for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth.list) {
                           if (!authAvailability.visible) {
                               continue;
                           }
                           if (application.runtimeSettings.lastAuth == null) {
                               if (authAvailability.name.equals("std") || this.authAvailability == null) {
                                   changeAuthAvailability(authAvailability);
                               }
                           } else if (authAvailability.name.equals(application.runtimeSettings.lastAuth.name))
                               changeAuthAvailability(authAvailability);
                           addAuthAvailability(authAvailability);
                       }
                       if (this.authAvailability == null && auth.list.size() > 0) {
                           changeAuthAvailability(auth.list.get(0));
                       }
                       runAutoAuth();
                   }), null);
    }

    private void runAutoAuth() {
        if (application.guiModuleConfig.autoAuth || application.runtimeSettings.autoAuth) {
            contextHelper.runInFxThread(this::loginWithGui);
        }
    }

    public void changeAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        boolean isChanged = this.authAvailability != authAvailability; //TODO: FIX
        this.authAvailability = authAvailability;
        this.application.authService.setAuthAvailability(authAvailability);
        this.authList.selectionModelProperty().get().select(authAvailability);
        authFlow.init(authAvailability);
        LogHelper.info("Selected auth: %s", authAvailability.name);
    }

    public void addAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        authList.getItems().add(authAvailability);
        LogHelper.info("Added %s: %s", authAvailability.name, authAvailability.displayName);
    }

    public <T extends WebSocketEvent> void processing(Request<T> request, String text, Consumer<T> onSuccess,
            Consumer<String> onError) {
        contextHelper.runInFxThread(() -> {
            processRequest(text, request, onSuccess, (thr) -> onError.accept(thr.getCause().getMessage()), null);
        });
    }


    @Override
    public void errorHandle(Throwable e) {
        super.errorHandle(e);
        contextHelper.runInFxThread(() -> {
            authButton.setState(LoginAuthButtonComponent.AuthButtonState.ERROR);
        });
    }

    @Override
    public void reset() {
        authFlow.reset();
        for(var e : authMethods.values()) {
            e.reset();
        }
    }

    @Override
    public String getName() {
        return "login";
    }

    private boolean tryOAuthLogin() {
        if (application.runtimeSettings.lastAuth != null && authAvailability.name.equals(
                application.runtimeSettings.lastAuth.name) && application.runtimeSettings.oauthAccessToken != null) {
            if (application.runtimeSettings.oauthExpire != 0
                    && application.runtimeSettings.oauthExpire < System.currentTimeMillis()) {
                refreshToken();
                return true;
            }
            Request.setOAuth(authAvailability.name,
                             new AuthRequestEvent.OAuthRequestEvent(application.runtimeSettings.oauthAccessToken,
                                                                    application.runtimeSettings.oauthRefreshToken,
                                                                    application.runtimeSettings.oauthExpire),
                             application.runtimeSettings.oauthExpire);
            AuthOAuthPassword password = new AuthOAuthPassword(application.runtimeSettings.oauthAccessToken);
            LogHelper.info("Login with OAuth AccessToken");
            loginWithOAuth(password, authAvailability, true);
            return true;
        }
        return false;
    }

    private void refreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest(authAvailability.name,
                                                              application.runtimeSettings.oauthRefreshToken);
        processing(request, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
            application.runtimeSettings.oauthAccessToken = result.oauth.accessToken;
            application.runtimeSettings.oauthRefreshToken = result.oauth.refreshToken;
            application.runtimeSettings.oauthExpire = result.oauth.expire == 0
                    ? 0
                    : System.currentTimeMillis() + result.oauth.expire;
            Request.setOAuth(authAvailability.name, result.oauth);
            AuthOAuthPassword password = new AuthOAuthPassword(application.runtimeSettings.oauthAccessToken);
            LogHelper.info("Login with OAuth AccessToken");
            loginWithOAuth(password, authAvailability, false);
        }, (error) -> {
            application.runtimeSettings.oauthAccessToken = null;
            application.runtimeSettings.oauthRefreshToken = null;
            contextHelper.runInFxThread(this::loginWithGui);
        });
    }

    private void loginWithOAuth(AuthOAuthPassword password,
            GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability, boolean refreshIfError) {
        AuthRequest authRequest = application.authService.makeAuthRequest(null, password, authAvailability.name);
        processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"),
                   (result) -> contextHelper.runInFxThread(() -> onSuccessLogin(new SuccessAuth(result, null, null))),
                   (error) -> {
                        if(refreshIfError && error.equals(AuthRequestEvent.OAUTH_TOKEN_EXPIRE)) {
                            refreshToken();
                            return;
                        }
                       if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
                           application.runtimeSettings.oauthAccessToken = null;
                           application.runtimeSettings.oauthRefreshToken = null;
                           contextHelper.runInFxThread(this::loginWithGui);
                       } else {
                           errorHandle(new RequestException(error));
                       }
                   });
    }

    @SuppressWarnings("unchecked")
    private AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> detailsToMethod(
            GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details) {
        return (AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>) authMethods.get(
                details.getClass());
    }

    private void loginWithGui() {
        authButton.setState(LoginAuthButtonComponent.AuthButtonState.UNACTIVE);
        {
            var method = authFlow.getAuthMethodOnShow();
            if (method != null) {
                method.onAuthClicked();
                return;
            }
        }
        if (tryOAuthLogin()) return;
        authFlow.start().thenAccept((result) -> contextHelper.runInFxThread(() -> onSuccessLogin(result)));
    }

    private boolean checkSavePasswordAvailable(AuthRequest.AuthPasswordInterface password) {
        if (password instanceof Auth2FAPassword) return false;
        if (password instanceof AuthMultiPassword) return false;
        return authAvailability != null
                && authAvailability.details != null
                && authAvailability.details.size() != 0
                && authAvailability.details.get(0) instanceof AuthPasswordDetails;
    }

    public void onSuccessLogin(SuccessAuth successAuth) {
        AuthRequestEvent result = successAuth.requestEvent;
        application.authService.setAuthResult(authAvailability.name, result);
        boolean savePassword = savePasswordCheckBox.isSelected();
        if (savePassword) {
            application.runtimeSettings.login = successAuth.recentLogin;
            if (result.oauth == null) {
                LogHelper.warning("Password not saved");
            } else {
                application.runtimeSettings.oauthAccessToken = result.oauth.accessToken;
                application.runtimeSettings.oauthRefreshToken = result.oauth.refreshToken;
                application.runtimeSettings.oauthExpire = Request.getTokenExpiredTime();
                application.runtimeSettings.password = null;
            }
            application.runtimeSettings.lastAuth = authAvailability;
        }
        if (result.playerProfile != null
                && result.playerProfile.assets != null) {
            try {
                Texture skin = result.playerProfile.assets.get("SKIN");
                Texture avatar = result.playerProfile.assets.get("AVATAR");
                if(skin != null || avatar != null) {
                    application.skinManager.addSkinWithAvatar(result.playerProfile.username,
                                                              skin != null ? new URL(skin.url) : null,
                                                              avatar != null ? new URL(avatar.url) : null);
                    application.skinManager.getSkin(result.playerProfile.username); //Cache skin
                }
            } catch (Exception e) {
                LogHelper.error(e);
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
        processing(new ProfilesRequest(), application.getTranslation("runtime.overlay.processing.text.profiles"),
                   (profiles) -> {
                       application.profilesService.setProfilesResult(profiles);
                       application.runtimeSettings.profiles = profiles.profiles;
                       contextHelper.runInFxThread(() -> {
                           application.securityService.startRequest();
                           if (application.gui.optionsScene != null) {
                               try {
                                   application.profilesService.loadAll();
                               } catch (Throwable ex) {
                                   errorHandle(ex);
                               }
                           }
                           if (application.getCurrentScene() instanceof LoginScene loginScene) {
                               loginScene.isLoginStarted = false;
                           }
                           application.setMainScene(application.gui.serverMenuScene);
                       });
                   }, null);
    }

    @SuppressWarnings("deprecation")
    public void clearPassword() {
        application.runtimeSettings.encryptedPassword = null;
        application.runtimeSettings.password = null;
        application.runtimeSettings.login = null;
        application.runtimeSettings.oauthAccessToken = null;
        application.runtimeSettings.oauthRefreshToken = null;
    }

    public record LoginAndPasswordResult(String login, AuthRequest.AuthPasswordInterface password) {
    }

    private static class AuthAvailabilityStringConverter extends StringConverter<GetAvailabilityAuthRequestEvent.AuthAvailability> {
        @Override
        public String toString(GetAvailabilityAuthRequestEvent.AuthAvailability object) {
            return object == null ? "null" : object.displayName;
        }

        @Override
        public GetAvailabilityAuthRequestEvent.AuthAvailability fromString(String string) {
            return null;
        }
    }

    public class LoginSceneAccessor {
        public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
            LoginScene.this.showOverlay(overlay, onFinished);
        }

        public void showContent(AbstractVisualComponent component) throws Exception {
            component.init();
            component.postInit();
            if (contentComponent != null) {
                content.getChildren().clear();
            }
            contentComponent = component;
            content.getChildren().add(component.getLayout());
        }

        public JavaFXApplication getApplication() {
            return application;
        }

        public LoginAuthButtonComponent getAuthButton() {
            return authButton;
        }

        public void errorHandle(Throwable e) {
            LoginScene.this.errorHandle(e);
        }
    }


    public class AuthFlow {
        private final List<Integer> authFlow = new ArrayList<>();
        private GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability;
        private volatile AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> authMethodOnShow;

        public void init(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
            this.authAvailability = authAvailability;
            reset();
        }

        public void reset() {
            authFlow.clear();
            authFlow.add(0);
            if (authMethodOnShow != null) {
                authMethodOnShow.onUserCancel();
            }
            if (content.getChildren().size() != 0) {
                content.getChildren().clear();
                authButton.setState(LoginAuthButtonComponent.AuthButtonState.ACTIVE);
            }
            if(authMethodOnShow != null && !authMethodOnShow.isOverlay()) {
                loginWithGui();
            }
            authMethodOnShow = null;
        }

        private CompletableFuture<LoginAndPasswordResult> tryLogin(String resentLogin,
                AuthRequest.AuthPasswordInterface resentPassword) {
            CompletableFuture<LoginScene.LoginAndPasswordResult> authFuture = null;
            if (resentPassword != null) {
                authFuture = new CompletableFuture<>();
                authFuture.complete(new LoginAndPasswordResult(resentLogin, resentPassword));
            }
            for (int i : authFlow) {
                GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details = authAvailability.details.get(i);
                final AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> authMethod = detailsToMethod(
                        details);
                if (authFuture == null) authFuture = authMethod.show(details).thenCompose((x) -> {
                    authMethodOnShow = authMethod;
                    return CompletableFuture.completedFuture(x);
                }).thenCompose((e) -> authMethod.auth(details)).thenCompose((x) -> {
                    authMethodOnShow = null;
                    return CompletableFuture.completedFuture(x);
                });
                else {
                    authFuture = authFuture.thenCompose(e -> authMethod.show(details).thenApply(x -> e));
                    authFuture = authFuture.thenCompose((x) -> {
                        authMethodOnShow = authMethod;
                        return CompletableFuture.completedFuture(x);
                    });
                    authFuture = authFuture.thenCompose(first -> authMethod.auth(details).thenApply(second -> {
                        AuthRequest.AuthPasswordInterface password;
                        String login = null;
                        if (first.login != null) {
                            login = first.login;
                        }
                        if (second.login != null) {
                            login = second.login;
                        }
                        if (first.password instanceof AuthMultiPassword authMultiPassword) {
                            password = first.password;
                            authMultiPassword.list.add(second.password);
                        } else if (first.password instanceof Auth2FAPassword auth2FAPassword) {
                            password = new AuthMultiPassword();
                            ((AuthMultiPassword) password).list = new ArrayList<>();
                            ((AuthMultiPassword) password).list.add(auth2FAPassword.firstPassword);
                            ((AuthMultiPassword) password).list.add(auth2FAPassword.secondPassword);
                            ((AuthMultiPassword) password).list.add(second.password);
                        } else {
                            password = new Auth2FAPassword();
                            ((Auth2FAPassword) password).firstPassword = first.password;
                            ((Auth2FAPassword) password).secondPassword = second.password;
                        }
                        return new LoginAndPasswordResult(login, password);
                    }));
                    authFuture = authFuture.thenCompose((x) -> {
                        authMethodOnShow = null;
                        return CompletableFuture.completedFuture(x);
                    });
                }
                authFuture = authFuture.thenCompose(e -> authMethod.hide().thenApply(x -> e));
            }
            return authFuture;
        }

        public AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> getAuthMethodOnShow() {
            return authMethodOnShow;
        }

        private void start(CompletableFuture<SuccessAuth> result, String resentLogin,
                AuthRequest.AuthPasswordInterface resentPassword) {
            CompletableFuture<LoginAndPasswordResult> authFuture = tryLogin(resentLogin, resentPassword);
            authFuture.thenAccept(e -> login(e.login, e.password, authAvailability, result)).exceptionally((e) -> {
                e = e.getCause();
                reset();
                isLoginStarted = false;
                if (e instanceof AbstractAuthMethod.UserAuthCanceledException) {
                    return null;
                }
                errorHandle(e);
                return null;
            });
        }

        private CompletableFuture<SuccessAuth> start() {
            CompletableFuture<SuccessAuth> result = new CompletableFuture<>();
            start(result, null, null);
            return result;
        }


        private void login(String login, AuthRequest.AuthPasswordInterface password,
                GetAvailabilityAuthRequestEvent.AuthAvailability authId, CompletableFuture<SuccessAuth> result) {
            isLoginStarted = true;
            LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
            AuthRequest authRequest = application.authService.makeAuthRequest(login, password, authId.name);
            processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"),
                       (event) -> result.complete(new SuccessAuth(event, login, password)), (error) -> {
                        if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
                            application.runtimeSettings.oauthAccessToken = null;
                            application.runtimeSettings.oauthRefreshToken = null;
                            result.completeExceptionally(new RequestException(error));
                        } else if (error.equals(AuthRequestEvent.TWO_FACTOR_NEED_ERROR_MESSAGE)) {
                            authFlow.clear();
                            authFlow.add(1);
                            contextHelper.runInFxThread(() -> start(result, login, password));
                        } else if (error.startsWith(AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX)) {
                            List<Integer> newAuthFlow = new ArrayList<>();
                            for (String s : error.substring(
                                    AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX.length() + 1).split("\\.")) {
                                newAuthFlow.add(Integer.parseInt(s));
                            }
                            //AuthRequest.AuthPasswordInterface recentPassword = makeResentPassword(newAuthFlow, password);
                            authFlow.clear();
                            authFlow.addAll(newAuthFlow);
                            contextHelper.runInFxThread(() -> start(result, login, password));
                        } else {
                            authFlow.clear();
                            authFlow.add(0);
                            errorHandle(new RequestException(error));
                        }
                    });
        }

    }

    public static class SuccessAuth {
        public AuthRequestEvent requestEvent;
        public String recentLogin;
        public AuthRequest.AuthPasswordInterface recentPassword;

        public SuccessAuth(AuthRequestEvent requestEvent, String recentLogin,
                AuthRequest.AuthPasswordInterface recentPassword) {
            this.requestEvent = requestEvent;
            this.recentLogin = recentLogin;
            this.recentPassword = recentPassword;
        }
    }
}
