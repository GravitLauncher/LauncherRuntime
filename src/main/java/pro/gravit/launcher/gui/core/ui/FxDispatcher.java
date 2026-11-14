package pro.gravit.launcher.gui.core.ui;

import javafx.application.Platform;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public enum FxDispatcher implements Executor {
    INSTANCE;

    public static void checkThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("FX thread required");
    }

    @Override
    public void execute(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }

    public <T> CompletableFuture<T> submit(Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            execute(() -> {
                if (result.isCancelled()) return;
                try { result.complete(action.call()); }
                catch (Throwable e) { result.completeExceptionally(e); }
            });
        } catch (RuntimeException e) { result.completeExceptionally(e); }
        return result;
    }

    public CompletableFuture<Void> run(Runnable action) {
        return submit(() -> { action.run(); return null; });
    }
}
