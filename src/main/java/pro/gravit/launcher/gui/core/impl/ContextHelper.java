package pro.gravit.launcher.gui.core.impl;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;

public class ContextHelper {
    private final FxComponent pane;

    ContextHelper(FxComponent pane) {
        this.pane = pane;
    }

    public static <T> CompletableFuture<T> runInFxThreadStatic(GuiExceptionCallback<T> callback) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (Platform.isFxApplicationThread()) {
            try {
                result.complete(callback.call());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        } else {
            Platform.runLater(() -> {
                try {
                    result.complete(callback.call());
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        }
        return result;
    }

    public static<T> CompletableFuture<T> runAfterTimeoutStatic(Duration duration, GuiExceptionCallback<T> callback) {
        CompletableFuture<T> result = new CompletableFuture<>();
        runInFxThreadStatic(() -> {
            PauseTransition timer = new PauseTransition(duration);
            timer.setOnFinished(event -> {
                if (result.isCancelled()) return;
                try { result.complete(callback.call()); }
                catch (Throwable error) { result.completeExceptionally(error); }
            });
            result.whenComplete((v, e) -> { if (result.isCancelled()) Platform.runLater(timer::stop); });
            timer.play();
        }).whenComplete((v, e) -> { if (e != null) result.completeExceptionally(e); });
        return result;
    }

    public <T> T runCallback(GuiExceptionCallback<T> callback) {
        try {
            return callback.call();
        } catch (Throwable throwable) {
            errorHandling(throwable);
            return null;
        }
    }

    public void runCallback(GuiExceptionRunnable callback) {
        try {
            callback.call();
        } catch (Throwable throwable) {
            errorHandling(throwable);
        }
    }

    public static CompletableFuture<Void> runInFxThreadStatic(GuiExceptionRunnable callback) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (Platform.isFxApplicationThread()) {
            try {
                callback.call();
                result.complete(null);
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        } else {
            Platform.runLater(() -> {
                try {
                    callback.call();
                    result.complete(null);
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        }
        return result;
    }

    public final <T> CompletableFuture<T> runInFxThread(GuiExceptionCallback<T> callback) {
        return runInFxThreadStatic(callback).whenComplete((value, error) -> { if (error != null) errorHandling(error); });
    }

    public final <T> CompletableFuture<T> runAfterTimeout(Duration duration, GuiExceptionCallback<T> callback) {
        return runAfterTimeoutStatic(duration, callback).whenComplete((value, error) -> { if (error != null) errorHandling(error); });
    }

    public final CompletableFuture<Void> runInFxThread(GuiExceptionRunnable callback) {
        return runInFxThreadStatic(callback).whenComplete((value, error) -> { if (error != null) errorHandling(error); });
    }

    final void errorHandling(Throwable e) {
        if (pane != null) {
            pane.errorHandle(e);
        }
    }

    public interface GuiExceptionCallback<T> {
        T call() throws Throwable;
    }

    public interface GuiExceptionRunnable {
        void call() throws Throwable;
    }
}
