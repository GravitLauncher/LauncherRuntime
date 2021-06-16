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
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.login.methods.AbstractAuthMethod;
import pro.gravit.launcher.client.gui.scenes.login.methods.LoginAndPasswordAuthMethod;
import pro.gravit.launcher.client.gui.scenes.login.methods.TotpAuthMethod;
import pro.gravit.launcher.client.gui.scenes.login.methods.WebAuthMethod;
import pro.gravit.launcher.client.gui.service.AuthService;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.events.request.LauncherRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.auth.RefreshTokenRequest;
import pro.gravit.launcher.request.auth.details.AuthPasswordDetails;
import pro.gravit.launcher.request.auth.details.AuthTotpDetails;
import pro.gravit.launcher.request.auth.details.AuthWebViewDetails;
import pro.gravit.launcher.request.auth.password.*;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
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
    private AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> authMethod;

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
        LoginSceneAccessor accessor = new LoginSceneAccessor();
        authMethods.put(AuthPasswordDetails.class, new LoginAndPasswordAuthMethod(accessor));
        authMethods.put(AuthWebViewDetails.class, new WebAuthMethod(accessor));
        authMethods.put(AuthTotpDetails.class, new TotpAuthMethod(accessor));
    }

    @Override
    public void doInit() {
        authButton = new LoginAuthButtonComponent(LookupHelper.lookup(layout, "#authButtonBlock"), application, (e) -> contextHelper.runCallback(this::loginWithGui));
        savePasswordCheckBox = LookupHelper.lookup(layout, "#leftPane", "#savePassword");
        if (application.runtimeSettings.password != null) {
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
        authList = (VBox) LookupHelper.<ScrollPane>lookup(layout, "#authList").getContent();
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

                hideOverlay(0, (event) -> {
                    if (application.runtimeSettings.password != null && application.runtimeSettings.autoAuth)
                        contextHelper.runCallback(this::loginWithGui);
                });
            }), null);
            if (!application.isDebugMode())
                processRequest(application.getTranslation("runtime.overlay.processing.text.launcher"), launcherRequest, (result) -> {
                    if (result.launcherExtendedToken != null) {
                        Request.addExtendedToken(LauncherRequestEvent.LAUNCHER_EXTENDED_TOKEN_NAME, result.launcherExtendedToken);
                    }
                    if (result.needUpdate) {
                        try {
                            LogHelper.debug("Start update processing");
                            application.securityService.update(result);
                            LogHelper.debug("Exit with Platform.exit");
                            Platform.exit();
                            return;
                        } catch (Throwable e) {
                            contextHelper.runInFxThread(() -> {
                                getCurrentOverlay().errorHandle(e);
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
                }, (event) -> LauncherEngine.exitLauncher(0));
        }
    }

    public void changeAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        this.authAvailability = authAvailability;
        authFlow.clear();
        authFlow.add(0);
        updateAuthMethod(authAvailability.details.get(0));
        LogHelper.info("Selected auth: %s | method %s", authAvailability.name, authMethod == null ? null : authMethod.getClass());
    }

    @SuppressWarnings("unchecked")
    public void updateAuthMethod(GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details) {
        if (this.authMethod != null) this.authMethod.hide();
        this.authMethod = (AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>) authMethods.get(details.getClass());
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
    private volatile boolean errorEnabled = false;

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
            Request.service.request(request).thenAccept((result) -> {
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
        errorEnabled = true;
    }

    @Override
    public void reset() {
        if (authMethod != null) {
            authMethod.reset();
        }
    }

    @Override
    public String getName() {
        return "login";
    }

    private final List<Integer> authFlow = new ArrayList<>();
    private CompletableFuture<LoginAndPasswordResult> authFuture;

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
                    login(null, password, authAvailability, savePasswordCheckBox.isSelected());
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
            login(null, password, authAvailability, savePasswordCheckBox.isSelected());
            return true;
        }
        return false;
    }

    private void loginWithGui() throws Exception {
        if (tryOAuthLogin()) return;
        for (int i : authFlow) {
            GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details = authAvailability.details.get(i);
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
        authFuture.thenAccept(e -> {
            boolean savePassword = savePasswordCheckBox.isSelected();
            login(e.login, e.password, authAvailability, savePassword);
        }).exceptionally((e) -> {
            e = e.getCause();
            authFuture = null;
            isLoginStarted = false;
            if (e instanceof AbstractAuthMethod.UserAuthCanceledException) {
                return null;
            }
            errorHandle(e);
            return null;
        });
    }

    private void login(String login, AuthRequest.AuthPasswordInterface password, GetAvailabilityAuthRequestEvent.AuthAvailability authId, boolean savePassword) {
        isLoginStarted = true;
        LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
        AuthRequest authRequest = authService.makeAuthRequest(login, password, authId.name);
        processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
            application.stateService.setAuthResult(authId.name, result);
            authFuture = null;
            if (savePassword) {
                application.runtimeSettings.login = login;
                if (result.oauth == null) {
                    application.runtimeSettings.password = password;
                } else {
                    application.runtimeSettings.oauthAccessToken = result.oauth.accessToken;
                    application.runtimeSettings.oauthRefreshToken = result.oauth.refreshToken;
                    application.runtimeSettings.oauthExpire = Request.getTokenExpiredTime();
                }
                application.runtimeSettings.lastAuth = authId;
            }
            if (result.playerProfile != null && result.playerProfile.skin != null) {
                try {
                    application.skinManager.addSkin(result.playerProfile.username, new URL(result.playerProfile.skin.url));
                    application.skinManager.getSkin(result.playerProfile.username); //Cache skin
                } catch (Exception ignored) {
                }
            }
            contextHelper.runInFxThread(() -> {
                Optional<Node> player = LookupHelper.lookupIfPossible(scene.getRoot(), "#player");
                if (player.isPresent()) {
                    LookupHelper.<Labeled>lookupIfPossible(player.get(), "#playerName").ifPresent(l -> l.setText(result.playerProfile.username.toUpperCase(Locale.ROOT)));
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

        }, (error) -> {
            authFuture = null;
            if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
                application.runtimeSettings.oauthAccessToken = null;
                application.runtimeSettings.oauthRefreshToken = null;
                try {
                    contextHelper.runInFxThread(this::loginWithGui);
                } catch (Exception e) {
                    errorHandle(e);
                }
            }
            else if (error.equals(AuthRequestEvent.TWO_FACTOR_NEED_ERROR_MESSAGE)) {
                authFlow.clear();
                authFlow.add(0);
                authFlow.add(1);
                try {
                    contextHelper.runInFxThread(this::loginWithGui);
                } catch (Exception e) {
                    errorHandle(e);
                }
            } else if (error.startsWith(AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX)) {
                authFlow.clear();
                for (String s : error.substring(AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX.length() + 1).split("\\.")) {
                    authFlow.add(Integer.parseInt(s));
                }
                try {
                    contextHelper.runInFxThread(this::loginWithGui);
                } catch (Exception e) {
                    errorHandle(e);
                }
            } else {
                errorHandle(new RequestException(error));
            }
        });
    }

    public void onGetProfiles() {
        processing(new ProfilesRequest(), application.getTranslation("runtime.overlay.processing.text.profiles"), (profiles) -> {
            application.stateService.setProfilesResult(profiles);
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
}
