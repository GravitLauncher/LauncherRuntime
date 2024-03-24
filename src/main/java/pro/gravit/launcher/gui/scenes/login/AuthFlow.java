package pro.gravit.launcher.gui.scenes.login;

import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.base.request.Request;
import pro.gravit.launcher.base.request.RequestException;
import pro.gravit.launcher.base.request.auth.AuthRequest;
import pro.gravit.launcher.base.request.auth.RefreshTokenRequest;
import pro.gravit.launcher.base.request.auth.details.AuthLoginOnlyDetails;
import pro.gravit.launcher.base.request.auth.details.AuthPasswordDetails;
import pro.gravit.launcher.base.request.auth.details.AuthTotpDetails;
import pro.gravit.launcher.base.request.auth.details.AuthWebViewDetails;
import pro.gravit.launcher.base.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.base.request.auth.password.AuthMultiPassword;
import pro.gravit.launcher.base.request.auth.password.AuthOAuthPassword;
import pro.gravit.launcher.gui.scenes.login.methods.*;
import pro.gravit.utils.helper.LogHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AuthFlow {
    public Map<Class<? extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>, AbstractAuthMethod<? extends GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>> authMethods = new HashMap<>(
            8);
    private final LoginScene.LoginSceneAccessor accessor;
    private final List<Integer> authFlow = new ArrayList<>();
    private GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability;
    private volatile AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> authMethodOnShow;
    private final Consumer<SuccessAuth> onSuccessAuth;
    public boolean isLoginStarted;

    public AuthFlow(LoginScene.LoginSceneAccessor accessor, Consumer<SuccessAuth> onSuccessAuth) {
        this.accessor = accessor;
        this.onSuccessAuth = onSuccessAuth;
        authMethods.put(AuthPasswordDetails.class, new LoginAndPasswordAuthMethod(accessor));
        authMethods.put(AuthWebViewDetails.class, new WebAuthMethod(accessor));
        authMethods.put(AuthTotpDetails.class, new TotpAuthMethod(accessor));
        authMethods.put(AuthLoginOnlyDetails.class, new LoginOnlyAuthMethod(accessor));
    }

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
        if (!accessor.isEmptyContent()) {
            accessor.clearContent();
            accessor.setState(LoginAuthButtonComponent.AuthButtonState.ACTIVE);
        }
        if (authMethodOnShow != null && !authMethodOnShow.isOverlay()) {
            loginWithGui();
        }
        authMethodOnShow = null;
        for (var e : authMethods.values()) {
            e.reset();
        }
    }

    private CompletableFuture<LoginAndPasswordResult> tryLogin(String resentLogin,
            AuthRequest.AuthPasswordInterface resentPassword) {
        CompletableFuture<LoginAndPasswordResult> authFuture = null;
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
            accessor.errorHandle(e);
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
        var application = accessor.getApplication();
        LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
        AuthRequest authRequest = application.authService.makeAuthRequest(login, password, authId.name);
        accessor.processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"),
                            (event) -> result.complete(new SuccessAuth(event, login, password)), (error) -> {
                    if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
                        application.runtimeSettings.oauthAccessToken = null;
                        application.runtimeSettings.oauthRefreshToken = null;
                        result.completeExceptionally(new RequestException(error));
                    } else if (error.equals(AuthRequestEvent.TWO_FACTOR_NEED_ERROR_MESSAGE)) {
                        authFlow.clear();
                        authFlow.add(1);
                        accessor.runInFxThread(() -> start(result, login, password));
                    } else if (error.startsWith(AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX)) {
                        List<Integer> newAuthFlow = new ArrayList<>();
                        for (String s : error.substring(
                                AuthRequestEvent.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX.length() + 1).split("\\.")) {
                            newAuthFlow.add(Integer.parseInt(s));
                        }
                        //AuthRequest.AuthPasswordInterface recentPassword = makeResentPassword(newAuthFlow, password);
                        authFlow.clear();
                        authFlow.addAll(newAuthFlow);
                        accessor.runInFxThread(() -> start(result, login, password));
                    } else {
                        authFlow.clear();
                        authFlow.add(0);
                        accessor.errorHandle(new RequestException(error));
                    }
                });
    }

    void loginWithGui() {
        accessor.setState(LoginAuthButtonComponent.AuthButtonState.UNACTIVE);
        {
            var method = getAuthMethodOnShow();
            if (method != null) {
                method.onAuthClicked();
                return;
            }
        }
        if (tryOAuthLogin()) return;
        start().thenAccept((result) -> {
            if (onSuccessAuth != null) {
                onSuccessAuth.accept(result);
            }
        });
    }


    private boolean tryOAuthLogin() {
        var application = accessor.getApplication();
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
        var application = accessor.getApplication();
        RefreshTokenRequest request = new RefreshTokenRequest(authAvailability.name,
                                                              application.runtimeSettings.oauthRefreshToken);
        accessor.processing(request, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
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
            accessor.runInFxThread(this::loginWithGui);
        });
    }

    private void loginWithOAuth(AuthOAuthPassword password,
            GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability, boolean refreshIfError) {
        var application = accessor.getApplication();
        AuthRequest authRequest = application.authService.makeAuthRequest(null, password, authAvailability.name);
        accessor.processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"),
                            (result) -> accessor.runInFxThread(
                                    () -> onSuccessAuth.accept(new SuccessAuth(result, null, null))),
                            (error) -> {
                                if (refreshIfError && error.equals(AuthRequestEvent.OAUTH_TOKEN_EXPIRE)) {
                                    refreshToken();
                                    return;
                                }
                                if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
                                    application.runtimeSettings.oauthAccessToken = null;
                                    application.runtimeSettings.oauthRefreshToken = null;
                                    accessor.runInFxThread(this::loginWithGui);
                                } else {
                                    accessor.errorHandle(new RequestException(error));
                                }
                            });
    }


    @SuppressWarnings("unchecked")
    private AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> detailsToMethod(
            GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails details) {
        return (AbstractAuthMethod<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails>) authMethods.get(
                details.getClass());
    }

    public void prepare() {
        authMethods.forEach((k, v) -> v.prepare());
    }

    public record LoginAndPasswordResult(String login, AuthRequest.AuthPasswordInterface password) {
    }

    public record SuccessAuth(AuthRequestEvent requestEvent, String recentLogin,
                                     AuthRequest.AuthPasswordInterface recentPassword) {
    }
}
