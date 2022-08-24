package pro.gravit.launcher.client.gui.scenes.login;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.StdJavaRuntimeProvider;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.login.methods.*;
import pro.gravit.launcher.client.gui.service.AuthService;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.events.request.LauncherRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.auth.RefreshTokenRequest;
import pro.gravit.launcher.request.auth.details.AuthLoginOnlyDetails;
import pro.gravit.launcher.request.auth.details.AuthPasswordDetails;
import pro.gravit.launcher.request.auth.details.AuthTotpDetails;
import pro.gravit.launcher.request.auth.details.AuthWebViewDetails;
import pro.gravit.launcher.request.auth.password.*;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.launcher.utils.LauncherUpdater;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoginScene extends AbstractScene {
    public Map<Class<? extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>, AbstractAuthMethod<? extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>> authMethods = new HashMap<>(8);
    public boolean isLoginStarted;
    private List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    private CheckBox savePasswordCheckBox;
    private CheckBox autoenter;
    private LoginAuthButtonComponent authButton;
    private final AuthService authService = new AuthService(application);
    private VBox authList;
    private ToggleGroup authToggleGroup;
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
        authButton = new LoginAuthButtonComponent(LookupHelper.lookup(layout, "#authButtonBlock"), application, (e) -> contextHelper.runCallback(this::loginWithGui));
        savePasswordCheckBox = LookupHelper.lookup(layout, "#leftPane", "#savePassword");
        if (application.runtimeSettings.password != null || application.runtimeSettings.oauthAccessToken != null) {
            LookupHelper.<CheckBox>lookup(layout, "#leftPane", "#savePassword").setSelected(true);
        }
        autoenter = LookupHelper.<CheckBox>lookup(layout, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());
        if (application.guiModuleConfig.createAccountURL != null)
            LookupHelper.<Text>lookup(header, "#controls", "#registerPane", "#createAccount").setOnMouseClicked((e) ->
                    application.openURL(application.guiModuleConfig.createAccountURL));
        if (application.guiModuleConfig.forgotPassURL != null)
            LookupHelper.<Text>lookup(header, "#controls", "#links", "#forgotPass").setOnMouseClicked((e) ->
                    application.openURL(application.guiModuleConfig.forgotPassURL));
        authList = LookupHelper.<VBox>lookup(layout, "#authList");
        authToggleGroup = new ToggleGroup();
        authMethods.forEach((k, v) -> v.prepare());
        // Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
            processRequest(application.getTranslation("runtime.overlay.processing.text.authAvailability"), getAvailabilityAuthRequest, (auth) -> contextHelper.runInFxThread(() -> {
                this.auth = auth.list;
                authList.setVisible(auth.list.size() != 1);
                for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth.list) {
                    if (application.runtimeSettings.lastAuth == null) {
                        if (authAvailability.name.equals("std") || this.authAvailability == null) {
                            changeAuthAvailability(authAvailability);
                        }
                    } else if (authAvailability.name.equals(application.runtimeSettings.lastAuth.name))
                        changeAuthAvailability(authAvailability);
                    addAuthAvailability(authAvailability);
                }
                if(this.authAvailability == null && auth.list.size() > 0) {
                    changeAuthAvailability(auth.list.get(0));
                }
                hideOverlay(0, (event) -> {
                    if (application.runtimeSettings.password != null && application.runtimeSettings.autoAuth)
                        contextHelper.runCallback(this::loginWithGui);
                    if(application.isDebugMode()) {
                        postInit();
                    }
                });
            }), null);
            if (!application.isDebugMode()) {
                processRequest(application.getTranslation("runtime.overlay.processing.text.launcher"), launcherRequest, (result) -> {
                    if (result.launcherExtendedToken != null) {
                        Request.addExtendedToken(LauncherRequestEvent.LAUNCHER_EXTENDED_TOKEN_NAME, result.launcherExtendedToken);
                    }
                    if (result.needUpdate) {
                        try {
                            LogHelper.debug("Start update processing");
                            disable();
                            StdJavaRuntimeProvider.updatePath = LauncherUpdater.prepareUpdate(new URL(result.url));
                            LogHelper.debug("Exit with Platform.exit");
                            Platform.exit();
                            return;
                        } catch (Throwable e) {
                            contextHelper.runInFxThread(() -> {
                                errorHandle(e);
                            });
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
                    postInit();
                }, (event) -> LauncherEngine.exitLauncher(0));
            }
        }
    }

    private void postInit() {
        if(application.guiModuleConfig.autoAuth || application.runtimeSettings.autoAuth) {
            contextHelper.runInFxThread(this::loginWithGui);
        }
    }

    public void changeAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        this.authAvailability = authAvailability;
        authFlow.init(authAvailability);
        LogHelper.info("Selected auth: %s", authAvailability.name);
    }

    public void addAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        RadioButton radio = new RadioButton();
        radio.setToggleGroup(authToggleGroup);
        radio.setId("authRadio");
        radio.setText(authAvailability.displayName);
        if (this.authAvailability == authAvailability) {
            radio.fire();
        }
        radio.setOnAction((e) -> {
            changeAuthAvailability(authAvailability);
        });
        LogHelper.info("Added %s: %s", authAvailability.name, authAvailability.displayName);
        authList.getChildren().add(radio);
    }

    private volatile boolean processingEnabled = false;

    public <T extends WebSocketEvent> void processing(Request<T> request, String text, Consumer<T> onSuccess, Consumer<String> onError) {
        Pane root = (Pane) scene.getRoot();
        LookupHelper.Point2D authAbsPosition = LookupHelper.getAbsoluteCords(authButton.getLayout(), layout);
        LogHelper.debug("X: %f, Y: %f", authAbsPosition.x, authAbsPosition.y);
        double authLayoutX = authButton.getLayout().getLayoutX();
        double authLayoutY = authButton.getLayout().getLayoutY();
        String oldText = authButton.getText();
        if (!processingEnabled) {
            contextHelper.runInFxThread(() -> {
                disable();
                layout.getChildren().remove(authButton.getLayout());
                root.getChildren().add(authButton.getLayout());
                authButton.getLayout().setLayoutX(authAbsPosition.x);
                authButton.getLayout().setLayoutY(authAbsPosition.y);
            });
            authButton.disable();
            processingEnabled = true;
        }
        contextHelper.runInFxThread(() -> {
            authButton.setText(text);
        });
        Runnable processingOff = () -> {
            if (!processingEnabled) return;
            contextHelper.runInFxThread(() -> {
                enable();
                root.getChildren().remove(authButton.getLayout());
                layout.getChildren().add(authButton.getLayout());
                authButton.getLayout().setLayoutX(authLayoutX);
                authButton.getLayout().setLayoutY(authLayoutY);
                authButton.setText(oldText);
            });
            authButton.enable();
            processingEnabled = false;
        };
        try {
            application.service.request(request).thenAccept((result) -> {
                onSuccess.accept(result);
                processingOff.run();
            }).exceptionally((exc) -> {
                onError.accept(exc.getCause().getMessage());
                processingOff.run();
                return null;
            });
        } catch (IOException e) {
            processingOff.run();
            errorHandle(e);
        }
    }


    @Override
    public void errorHandle(Throwable e) {
        super.errorHandle(e);
        Pane root = (Pane) scene.getRoot();
        double authLayoutX = authButton.getLayout().getLayoutX();
        double authLayoutY = authButton.getLayout().getLayoutY();
        if (!processingEnabled) return;
        contextHelper.runInFxThread(() -> {
            enable();
            root.getChildren().remove(authButton.getLayout());
            layout.getChildren().add(authButton.getLayout());
            authButton.getLayout().setLayoutX(authLayoutX);
            authButton.getLayout().setLayoutY(authLayoutY);
            authButton.setText("ERROR");
            authButton.setError();
        });
        authButton.enable();
        processingEnabled = false;
    }

    @Override
    public void reset() {

    }

    @Override
    public String getName() {
        return "login";
    }

    private boolean tryOAuthLogin() {
        if (application.runtimeSettings.lastAuth != null && authAvailability.name.equals(application.runtimeSettings.lastAuth.name) && application.runtimeSettings.oauthAccessToken != null) {
            if (application.runtimeSettings.oauthExpire != 0 && application.runtimeSettings.oauthExpire < System.currentTimeMillis()) {
                RefreshTokenRequest request = new RefreshTokenRequest(authAvailability.name, application.runtimeSettings.oauthRefreshToken);
                processing(request, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
                    application.runtimeSettings.oauthAccessToken = result.oauth.accessToken;
                    application.runtimeSettings.oauthRefreshToken = result.oauth.refreshToken;
                    application.runtimeSettings.oauthExpire = result.oauth.expire == 0 ? 0 : System.currentTimeMillis() + result.oauth.expire;
                    Request.setOAuth(authAvailability.name, result.oauth);
                    AuthOAuthPassword password = new AuthOAuthPassword(application.runtimeSettings.oauthAccessToken);
                    LogHelper.info("Login with OAuth AccessToken");
                    loginWithOAuth(password, authAvailability);
                }, (error) -> {
                    application.runtimeSettings.oauthAccessToken = null;
                    application.runtimeSettings.oauthRefreshToken = null;
                    contextHelper.runInFxThread(this::loginWithGui);
                });
                return true;
            }
            Request.setOAuth(authAvailability.name, new AuthRequestEvent.OAuthRequestEvent(application.runtimeSettings.oauthAccessToken, application.runtimeSettings.oauthRefreshToken, application.runtimeSettings.oauthExpire), application.runtimeSettings.oauthExpire);
            AuthOAuthPassword password = new AuthOAuthPassword(application.runtimeSettings.oauthAccessToken);
            LogHelper.info("Login with OAuth AccessToken");
            loginWithOAuth(password, authAvailability);
            return true;
        }
        return false;
    }

    private void loginWithOAuth(AuthOAuthPassword password, GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        AuthRequest authRequest = authService.makeAuthRequest(null, password, authAvailability.name);
        processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
            contextHelper.runInFxThread(() -> {
                onSuccessLogin(new SuccessAuth(result, null, null));
            });
        }, (error) -> {
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
    private AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> detailsToMethod(GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details) {
        return (AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>) authMethods.get(details.getClass());
    }

    private void loginWithGui() {
        authButton.unsetError();
        if (tryOAuthLogin()) return;
        authFlow.start().thenAccept((result) -> {
            contextHelper.runInFxThread(() -> {
                onSuccessLogin(result);
            });
        });
    }

    private boolean checkSavePasswordAvailable(AuthRequest.AuthPasswordInterface password) {
        if(password instanceof Auth2FAPassword)
            return false;
        if(password instanceof AuthMultiPassword)
            return false;
        if(authAvailability == null || authAvailability.details == null || authAvailability.details.size() == 0 || !( authAvailability.details.get(0) instanceof AuthPasswordDetails ) )
            return false;
        return true;
    }

    private void onSuccessLogin(SuccessAuth successAuth) {
        AuthRequestEvent result = successAuth.requestEvent;
        application.stateService.setAuthResult(authAvailability.name, result);
        boolean savePassword = savePasswordCheckBox.isSelected();
        if (savePassword) {
            application.runtimeSettings.login = successAuth.recentLogin;
            if (result.oauth == null) {
                if(successAuth.recentPassword != null && checkSavePasswordAvailable(successAuth.recentPassword)) {
                    application.runtimeSettings.password = successAuth.recentPassword;
                } else {
                    LogHelper.warning("2FA/MFA Password not saved");
                }
            } else {
                application.runtimeSettings.oauthAccessToken = result.oauth.accessToken;
                application.runtimeSettings.oauthRefreshToken = result.oauth.refreshToken;
                application.runtimeSettings.oauthExpire = Request.getTokenExpiredTime();
            }
            application.runtimeSettings.lastAuth = authAvailability;
        }
        if (result.playerProfile != null && result.playerProfile.assets != null && result.playerProfile.assets.get("SKIN") != null) {
            try {
                application.skinManager.addSkin(result.playerProfile.username, new URL(result.playerProfile.assets.get("SKIN").url));
                application.skinManager.getSkin(result.playerProfile.username); //Cache skin
            } catch (Exception ignored) {
            }
        }
        contextHelper.runInFxThread(() -> {
            Optional<Node> player = LookupHelper.lookupIfPossible(scene.getRoot(), "#player");
            if (player.isPresent()) {
                LookupHelper.<Label>lookupIfPossible(player.get(), "#playerName").ifPresent((e) -> e.setText(application.stateService.getUsername()));
                LookupHelper.<ImageView>lookupIfPossible(player.get(), "#playerHead").ifPresent(
                        (h) -> {
                            try {
                                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(h.getFitWidth(), h.getFitHeight());
                                clip.setArcWidth(h.getFitWidth());
                                clip.setArcHeight(h.getFitHeight());
                                h.setClip(clip);
                                Image image = application.skinManager.getScaledFxSkinHead(result.playerProfile.username, (int) h.getFitWidth(), (int) h.getFitHeight());
                                if (image != null)
                                    h.setImage(image);
                            } catch (Throwable e) {
                                LogHelper.warning("Skin head error");
                            }
                        }
                );
                player.get().setVisible(true);
                disable();
                fade(player.get(), 2000.0, 0.0, 1.0, (e) -> {
                            enable();
                            onGetProfiles();
                            player.get().setVisible(false);
                        }
                );
            } else {
                onGetProfiles();
            }
        });
    }

    public void onGetProfiles() {
        processing(new ProfilesRequest(), application.getTranslation("runtime.overlay.processing.text.profiles"), (profiles) -> {
            application.stateService.setProfilesResult(profiles);
            application.runtimeSettings.profiles = profiles.profiles;
            for (ClientProfile profile : profiles.profiles) {
                application.triggerManager.process(profile, application.stateService.getOptionalView(profile));
            }
            contextHelper.runInFxThread(() -> {
                hideOverlay(0, null);
                application.securityService.startRequest();
                if (application.gui.optionsScene != null) {
                    try {
                        application.gui.optionsScene.loadAll();
                    } catch (Throwable ex) {
                        errorHandle(ex);
                    }
                }
                if (application.getCurrentScene() instanceof LoginScene) {
                    ((LoginScene) application.getCurrentScene()).isLoginStarted = false;
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

    public static class LoginAndPasswordResult {
        public final String login;
        public final AuthRequest.AuthPasswordInterface password;

        public LoginAndPasswordResult(String login, AuthRequest.AuthPasswordInterface password) {
            this.login = login;
            this.password = password;
        }
    }

    public class LoginSceneAccessor {
        public void showOverlay(AbstractOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
            LoginScene.this.showOverlay(overlay, onFinished);
        }

        public void hideOverlay(double delay, EventHandler<ActionEvent> onFinished) {
            LoginScene.this.hideOverlay(delay, onFinished);
        }

        public AuthService getAuthService() {
            return authService;
        }

        public JavaFXApplication getApplication() {
            return application;
        }

        public void errorHandle(Throwable e) {
            LoginScene.this.errorHandle(e);
        }
    }



    public class AuthFlow {
        private final List<Integer> authFlow = new ArrayList<>();
        private GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability;

        public void init(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
            this.authAvailability = authAvailability;
            authFlow.clear();
            authFlow.add(0);
        }

        private CompletableFuture<LoginAndPasswordResult> tryLogin(String resentLogin, AuthRequest.AuthPasswordInterface resentPassword) {
            CompletableFuture<LoginScene.LoginAndPasswordResult> authFuture = null;
            if(resentPassword != null) {
                authFuture = new CompletableFuture<>();
                authFuture.complete(new LoginAndPasswordResult(resentLogin, resentPassword));
            }
            for (int i : authFlow) {
                GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details = authAvailability.details.get(i);
                final AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> authMethod = detailsToMethod(details);
                if (authFuture == null) authFuture = authMethod.show(details).thenCompose((e) -> authMethod.auth(details));
                else {
                    authFuture = authFuture.thenCompose(e -> authMethod.show(details).thenApply(x -> e));
                    authFuture = authFuture.thenCompose(first -> authMethod.auth(details).thenApply(second -> {
                        AuthRequest.AuthPasswordInterface password;
                        String login = null;
                        if (first.login != null) {
                            login = first.login;
                        }
                        if (second.login != null) {
                            login = second.login;
                        }
                        if (first.password instanceof AuthMultiPassword) {
                            password = first.password;
                            ((AuthMultiPassword) password).list.add(second.password);
                        } else if (first.password instanceof Auth2FAPassword) {
                            password = new AuthMultiPassword();
                            ((AuthMultiPassword) password).list = new ArrayList<>();
                            ((AuthMultiPassword) password).list.add(((Auth2FAPassword) first.password).firstPassword);
                            ((AuthMultiPassword) password).list.add(((Auth2FAPassword) first.password).secondPassword);
                            ((AuthMultiPassword) password).list.add(second.password);
                        } else {
                            password = new Auth2FAPassword();
                            ((Auth2FAPassword) password).firstPassword = first.password;
                            ((Auth2FAPassword) password).secondPassword = second.password;
                        }
                        return new LoginAndPasswordResult(login, password);
                    }));
                }
                authFuture = authFuture.thenCompose(e -> authMethod.hide().thenApply(x -> e));
            }
            return authFuture;
        }

        private void start(CompletableFuture<SuccessAuth> result, String resentLogin, AuthRequest.AuthPasswordInterface resentPassword) {
            CompletableFuture<LoginAndPasswordResult> authFuture = tryLogin(resentLogin, resentPassword);
            authFuture.thenAccept(e -> {
                login(e.login, e.password, authAvailability, result);
            }).exceptionally((e) -> {
                e = e.getCause();
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


        private void login(String login, AuthRequest.AuthPasswordInterface password, GetAvailabilityAuthRequestEvent.AuthAvailability authId, CompletableFuture<SuccessAuth> result) {
            isLoginStarted = true;
            LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
            AuthRequest authRequest = authService.makeAuthRequest(login, password, authId.name);
            processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"), (event) -> {
                result.complete(new SuccessAuth(event, login, password));
            }, (error) -> {
                if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
                    application.runtimeSettings.oauthAccessToken = null;
                    application.runtimeSettings.oauthRefreshToken = null;
                    result.completeExceptionally(new RequestException(error));
                } else if (error.equals(AuthRequestEvent.TWO_FACTOR_NEED_ERROR_MESSAGE)) {
                    /*List<Integer> newAuthFlow = new ArrayList<>();
                    newAuthFlow.add(0);
                    newAuthFlow.add(1);
                    AuthRequest.AuthPasswordInterface recentPassword = makeResentPassword(newAuthFlow, password);
                    authFlow.clear();
                    authFlow.addAll(newAuthFlow);*/
                    authFlow.clear();
                    authFlow.add(1);
                    contextHelper.runInFxThread(() -> start(result, login, password));
                } else if (error.startsWith(AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX)) {
                    List<Integer> newAuthFlow = new ArrayList<>();
                    for (String s : error.substring(AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX.length() + 1).split("\\.")) {
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

        /*public AuthRequest.AuthPasswordInterface makeResentPassword(List<Integer> newAuthFlow, AuthRequest.AuthPasswordInterface password) {
            List<AuthRequest.AuthPasswordInterface> list = authService.getListFromPassword(password);
            List<AuthRequest.AuthPasswordInterface> result = new ArrayList<>();
            for(int i=0;i<newAuthFlow.size();++i) {
                int flowId = newAuthFlow.get(i);
                if(authFlow.contains(flowId)) {
                    result.add(list.get(i));
                    //noinspection RedundantCast
                    newAuthFlow.remove((int)i);
                    i--;
                }
            }
            return authService.getPasswordFromList(result);
        }*/
    }

    public static class SuccessAuth {
        public AuthRequestEvent requestEvent;
        public String recentLogin;
        public AuthRequest.AuthPasswordInterface recentPassword;

        public SuccessAuth(AuthRequestEvent requestEvent, String recentLogin, AuthRequest.AuthPasswordInterface recentPassword) {
            this.requestEvent = requestEvent;
            this.recentLogin = recentLogin;
            this.recentPassword = recentPassword;
        }
    }
}
