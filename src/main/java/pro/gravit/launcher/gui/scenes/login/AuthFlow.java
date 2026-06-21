package pro.gravit.launcher.gui.scenes.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.request.RequestException;
import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.api.method.AuthMethodDetails;
import pro.gravit.launcher.core.api.method.AuthMethodPassword;
import pro.gravit.launcher.core.api.method.details.*;
import pro.gravit.launcher.core.api.method.password.AuthChainPassword;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.core.backend.UserSettings;
import pro.gravit.launcher.gui.scenes.login.methods.*;
import pro.gravit.launcher.runtime.backend.BackendSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AuthFlow {

    private static final Logger logger =
            LoggerFactory.getLogger(AuthFlow.class);

    public Map<Class<? extends AuthMethodDetails>, AbstractAuthMethod<? extends AuthMethodDetails>> authMethods = new HashMap<>(
            8);
    private final LoginScene.LoginSceneAccessor accessor;
    private final List<Integer> authFlow = new ArrayList<>();
    private AuthMethod authAvailability;
    private volatile AbstractAuthMethod<AuthMethodDetails> authMethodOnShow;
    private final Consumer<SuccessAuth> onSuccessAuth;
    public boolean isLoginStarted;

    public AuthFlow(LoginScene.LoginSceneAccessor accessor, Consumer<SuccessAuth> onSuccessAuth) {
        this.accessor = accessor;
        this.onSuccessAuth = onSuccessAuth;
        authMethods.put(AuthPasswordDetails.class, new LoginAndPasswordAuthMethod(accessor));
        authMethods.put(AuthWebDetails.class, new WebAuthMethod(accessor));
        authMethods.put(AuthDeviceFlowDetails.class, new DeviceFlowAuthMethod(accessor));
        authMethods.put(AuthTotpDetails.class, new TotpAuthMethod(accessor));
        authMethods.put(AuthLoginOnlyDetails.class, new LoginOnlyAuthMethod(accessor));
    }

    public void init(AuthMethod authAvailability) {
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
            accessor.setState(AuthButton.AuthButtonState.ACTIVE);
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
            AuthMethodPassword resentPassword) {
        CompletableFuture<LoginAndPasswordResult> authFuture = null;
        if (resentPassword != null) {
            authFuture = new CompletableFuture<>();
            authFuture.complete(new LoginAndPasswordResult(resentLogin, resentPassword));
        }
        for (int i : authFlow) {
            var details = authAvailability.getDetails().get(i);
            final var authMethod = detailsToMethod(
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
                    AuthMethodPassword password;
                    String login = null;
                    if (first.login != null) {
                        login = first.login;
                    }
                    if (second.login != null) {
                        login = second.login;
                    }
                    if (first.password instanceof AuthChainPassword authMultiPassword) {
                        password = first.password;
                        authMultiPassword.list().add(second.password);
                    } else {
                        password = new AuthChainPassword(List.of(first.password, second.password));
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

    public AbstractAuthMethod<AuthMethodDetails> getAuthMethodOnShow() {
        return authMethodOnShow;
    }

    private void start(CompletableFuture<SuccessAuth> result, String resentLogin,
            AuthMethodPassword resentPassword) {
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


    private void login(String login, AuthMethodPassword password,
            AuthMethod authId, CompletableFuture<SuccessAuth> result) {
        isLoginStarted = true;
        var application = accessor.getApplication();
        logger.info("Auth with {} password ***** authId {}", login, authId);
        accessor.processing(LauncherBackendAPIHolder.getApi().authorize(login, password), application.getTranslation("runtime.overlay.processing.text.auth"),
                            (event) -> result.complete(new SuccessAuth(event, login, password)), (error) -> {
                    if (error.equals(AuthRequestEvent.OAUTH_TOKEN_INVALID)) {
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
                        //AuthMethodPassword recentPassword = makeResentPassword(newAuthFlow, password);
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
        accessor.setState(AuthButton.AuthButtonState.UNACTIVE);
        {
            var method = getAuthMethodOnShow();
            if (method != null) {
                method.onAuthClicked();
                return;
            }
        }

        // Пробуем тихую авторизацию только если токен есть И провайдер совпадает с сохранённым
        if (hasOAuthToken()) {
            tryAutoLogin().thenAccept(success -> {
                if (!success) {
                    accessor.runInFxThread(() -> start().thenAccept((result) -> {
                        if (onSuccessAuth != null) {
                            onSuccessAuth.accept(result);
                        }
                    }));
                }
            });
            return;
        }

        start().thenAccept((result) -> {
            if (onSuccessAuth != null) {
                onSuccessAuth.accept(result);
            }
        });
    }

    private boolean hasOAuthToken() {
        UserSettings settings = LauncherBackendAPIHolder.getApi().getUserSettings("backend", (a) -> null);
        if (settings instanceof BackendSettings backendSettings) {
            return backendSettings.auth != null && backendSettings.auth.accessToken != null;
        }
        return false;
    }

    public CompletableFuture<Boolean> tryAutoLogin() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        accessor.processing(
                LauncherBackendAPIHolder.getApi().tryAuthorize(),
                accessor.getApplication().getTranslation("runtime.overlay.processing.text.auth"),
                (user) -> {
                    onSuccessAuth.accept(new SuccessAuth(user, null, null));
                    result.complete(true);
                },
                (error) -> result.complete(false)
        );
        return result;
    }


    @SuppressWarnings("unchecked")
    private AbstractAuthMethod<AuthMethodDetails> detailsToMethod(
            AuthMethodDetails details) {
        return (AbstractAuthMethod<AuthMethodDetails>) authMethods.get(
                details.getClass());
    }

    public void prepare() {
        authMethods.forEach((k, v) -> v.prepare());
    }

    public record LoginAndPasswordResult(String login, AuthMethodPassword password) {
    }

    public record SuccessAuth(SelfUser user, String recentLogin,
                              AuthMethodPassword recentPassword) {
    }
}