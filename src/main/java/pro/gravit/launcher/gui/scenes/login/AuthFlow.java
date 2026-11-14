package pro.gravit.launcher.gui.scenes.login;

import pro.gravit.launcher.core.api.method.AuthMethod;
import pro.gravit.launcher.core.api.method.AuthMethodDetails;
import pro.gravit.launcher.core.api.method.AuthMethodPassword;
import pro.gravit.launcher.core.api.method.details.*;
import pro.gravit.launcher.core.api.method.password.AuthChainPassword;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;
import pro.gravit.launcher.gui.core.utils.AuthConstants;
import pro.gravit.launcher.gui.scenes.login.methods.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Collects UI factors; credential storage and session validity belong to the backend. */
public final class AuthFlow {
    private final Map<Class<? extends AuthMethodDetails>, AbstractAuthMethod<?>> methods = new HashMap<>();
    private final LoginScene.LoginSceneAccessor accessor;
    private final Consumer<SuccessAuth> onSuccess;
    private AuthMethod availability;
    private AbstractAuthMethod<AuthMethodDetails> showing;
    private CompletableFuture<SuccessAuth> currentAttempt;
    private CompletableFuture<Boolean> autoAttempt;
    private long generation;
    public boolean isLoginStarted;

    public AuthFlow(LoginScene.LoginSceneAccessor accessor, Consumer<SuccessAuth> onSuccess) {
        this.accessor = accessor;
        this.onSuccess = onSuccess;
        methods.put(AuthPasswordDetails.class, new LoginAndPasswordAuthMethod(accessor));
        methods.put(AuthWebDetails.class, new WebAuthMethod(accessor));
        methods.put(AuthDeviceFlowDetails.class, new DeviceFlowAuthMethod(accessor));
        methods.put(AuthTotpDetails.class, new TotpAuthMethod(accessor));
        methods.put(AuthLoginOnlyDetails.class, new LoginOnlyAuthMethod(accessor));
    }

    public void init(AuthMethod method) { reset(); availability = method; }

    public void reset() {
        generation++;
        var pending = currentAttempt;
        currentAttempt = null;
        if (pending != null) pending.cancel(false);
        if (autoAttempt != null) { autoAttempt.cancel(false); autoAttempt = null; }
        isLoginStarted = false;
        if (showing != null) showing.onUserCancel();
        showing = null;
        methods.values().forEach(AbstractAuthMethod::reset);
        if (!accessor.isEmptyContent()) accessor.clearContent();
        accessor.setState(AuthButton.AuthButtonState.ACTIVE);
    }

    public AbstractAuthMethod<AuthMethodDetails> getAuthMethodOnShow() { return showing; }
    public void prepare() { methods.values().forEach(AbstractAuthMethod::prepare); }

    void loginWithGui() {
        FxDispatcher.checkThread();
        if (showing != null) { showing.onAuthClicked(); return; }
        if (isLoginStarted) return;
        var result = new CompletableFuture<SuccessAuth>();
        currentAttempt = result;
        isLoginStarted = true;
        long attempt = ++generation;
        accessor.getApplication().beginOperation();
        result.whenComplete((success, error) -> FxDispatcher.INSTANCE.execute(() -> {
            accessor.getApplication().endOperation();
            if (attempt != generation) return;
            currentAttempt = null;
            isLoginStarted = false;
            showing = null;
            methods.values().forEach(AbstractAuthMethod::reset);
            if (error == null) onSuccess.accept(success);
            else if (!(unwrap(error) instanceof java.util.concurrent.CancellationException)
                    && !(unwrap(error) instanceof AbstractAuthMethod.UserAuthCanceledException)) accessor.errorHandle(unwrap(error));
            else accessor.setState(AuthButton.AuthButtonState.ACTIVE);
        }));
        collectAndAuthorize(attempt, result, List.of(0), null);
    }

    private void collectAndAuthorize(long attempt, CompletableFuture<SuccessAuth> result,
                                     List<Integer> indexes, LoginAndPasswordResult previous) {
        if (attempt != generation || result.isDone()) return;
        collect(attempt, indexes, previous).whenComplete((credentials, error) -> FxDispatcher.INSTANCE.execute(() -> {
            if (attempt != generation || result.isDone()) return;
            if (error != null) { result.completeExceptionally(unwrap(error)); return; }
            try {
                var request = LauncherBackendAPIHolder.getApi().authorize(credentials.login(), credentials.password());
                accessor.processRequest(accessor.getApplication().getTranslation("runtime.overlay.processing.text.auth"), request,
                        user -> { if (attempt == generation) result.complete(new SuccessAuth(user, null, null)); },
                        failure -> {
                            if (attempt != generation || result.isDone()) return;
                            String message = unwrap(failure).getMessage();
                            try {
                                if (AuthConstants.TWO_FACTOR_NEED_ERROR_MESSAGE.equals(message)) {
                                    validateIndexes(List.of(1));
                                    collectAndAuthorize(attempt, result, List.of(1), credentials);
                                } else if (message != null && message.startsWith(AuthConstants.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX)) {
                                    collectAndAuthorize(attempt, result, parseFactorIndexes(message, availability.getDetails().size()), credentials);
                                } else result.completeExceptionally(unwrap(failure));
                            } catch (RuntimeException invalid) { result.completeExceptionally(invalid); }
                        }, event -> result.completeExceptionally(new IllegalStateException("Authentication UI failed")));
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        }));
    }

    private CompletableFuture<LoginAndPasswordResult> collect(long attempt, List<Integer> indexes,
                                                             LoginAndPasswordResult previous) {
        try { validateIndexes(indexes); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        CompletableFuture<LoginAndPasswordResult> chain = CompletableFuture.completedFuture(previous);
        for (int index : indexes) {
            AuthMethodDetails details = availability.getDetails().get(index);
            AbstractAuthMethod<AuthMethodDetails> method = method(details);
            chain = chain.thenComposeAsync(existing -> {
                if (attempt != generation) return CompletableFuture.failedFuture(new AbstractAuthMethod.UserAuthCanceledException());
                showing = method;
                return method.show(details).thenComposeAsync(ignored -> attempt == generation ? method.auth(details)
                        : CompletableFuture.failedFuture(new AbstractAuthMethod.UserAuthCanceledException()), FxDispatcher.INSTANCE)
                        .thenComposeAsync(next -> method.hide().thenApply(ignored -> merge(existing, next)), FxDispatcher.INSTANCE)
                        .thenApply(value -> { showing = null; return value; });
            }, FxDispatcher.INSTANCE);
        }
        return chain;
    }

    private void validateIndexes(List<Integer> indexes) {
        if (availability == null || availability.getDetails() == null || indexes.isEmpty()) throw new IllegalArgumentException("Authentication factors are unavailable");
        for (int index : indexes) {
            if (index < 0 || index >= availability.getDetails().size() || method(availability.getDetails().get(index)) == null)
                throw new IllegalArgumentException("Unsupported authentication factor: " + index);
        }
    }

    static LoginAndPasswordResult merge(LoginAndPasswordResult first, LoginAndPasswordResult second) {
        if (first == null) return second;
        List<AuthMethodPassword> passwords = new ArrayList<>();
        if (first.password() instanceof AuthChainPassword chain) passwords.addAll(chain.list());
        else passwords.add(first.password());
        passwords.add(second.password());
        return new LoginAndPasswordResult(second.login() == null ? first.login() : second.login(), new AuthChainPassword(passwords));
    }

    public CompletableFuture<Boolean> tryAutoLogin() {
        FxDispatcher.checkThread();
        if (isLoginStarted) return CompletableFuture.completedFuture(false);
        long attempt = ++generation;
        var result = new CompletableFuture<Boolean>();
        autoAttempt = result;
        isLoginStarted = true;
        accessor.getApplication().beginOperation();
        result.whenComplete((value, error) -> FxDispatcher.INSTANCE.execute(() -> {
            accessor.getApplication().endOperation();
            if (attempt == generation) { isLoginStarted = false; autoAttempt = null; }
        }));
        try {
            accessor.processRequest(accessor.getApplication().getTranslation("runtime.overlay.processing.text.auth"),
                    LauncherBackendAPIHolder.getApi().tryAuthorize(), user -> {
                        if (attempt == generation && !result.isDone()) {
                            isLoginStarted = false;
                            try {
                                onSuccess.accept(new SuccessAuth(user, null, null));
                                result.complete(true);
                            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
                        }
                    }, error -> { if (attempt == generation) isLoginStarted = false; result.complete(false); },
                    event -> { if (attempt == generation) isLoginStarted = false; result.complete(false); });
        } catch (RuntimeException error) { result.complete(false); }
        return result;
    }

    static List<Integer> parseFactorIndexes(String error, int count) {
        String prefix = AuthConstants.ONE_FACTOR_NEED_ERROR_MESSAGE_PREFIX;
        if (error == null || !error.startsWith(prefix)) throw new IllegalArgumentException("Invalid factor response");
        List<Integer> indexes = new ArrayList<>();
        for (String value : error.substring(prefix.length()).split("\\.", -1)) {
            int index;
            try { index = Integer.parseInt(value); }
            catch (NumberFormatException failure) { throw new IllegalArgumentException("Invalid factor index", failure); }
            if (index < 0 || index >= count) throw new IllegalArgumentException("Factor index is out of range");
            indexes.add(index);
        }
        return indexes;
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof java.util.concurrent.ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }
    @SuppressWarnings("unchecked")
    private AbstractAuthMethod<AuthMethodDetails> method(AuthMethodDetails details) {
        return details == null ? null : (AbstractAuthMethod<AuthMethodDetails>) methods.get(details.getClass());
    }
    public record LoginAndPasswordResult(String login, AuthMethodPassword password) { }
    public record SuccessAuth(SelfUser user, String recentLogin, AuthMethodPassword recentPassword) { }
}
