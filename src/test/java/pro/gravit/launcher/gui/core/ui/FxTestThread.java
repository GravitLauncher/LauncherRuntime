package pro.gravit.launcher.gui.core.ui;

import javafx.application.Platform;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FxTestThread {
    private static final long TIMEOUT_SECONDS = 30;
    private static final AtomicBoolean started = new AtomicBoolean();

    private FxTestThread() { }

    public static synchronized void start() throws Exception {
        if (started.get()) return;
        CompletableFuture<Void> initialized = new CompletableFuture<>();
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                initialized.complete(null);
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                initialized.complete(null);
            });
        }
        initialized.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        started.set(true);
    }

    public static <T> T call(Callable<T> action) throws Exception {
        start();
        if (Platform.isFxApplicationThread()) return action.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static void run(Runnable action) throws Exception {
        call(() -> { action.run(); return null; });
    }

    public static void shutdown() {
        if (started.get()) Platform.exit();
    }
}
