package pro.gravit.launcher.gui.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class OneTimeAction {
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    public void run(Runnable runnable) {
        boolean alreadyRunning = running.getAndSet(true);
        if (!alreadyRunning) {
            return;
        }
        CompletableFuture.runAsync(runnable);
    }

    public void run(Runnable runnable, Executor executor) {
        boolean alreadyRunning = running.getAndSet(true);
        if (!alreadyRunning) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            runnable.run();
            running.set(false);
        }, executor);
    }
}
