package pro.gravit.launcher.gui.basic;

import javafx.application.Platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class FxThreadExecutor implements ExecutorService {
    private static final FxThreadExecutor INSTANCE = new FxThreadExecutor();

    public static FxThreadExecutor getInstance() {
        return INSTANCE;
    }
    @Override
    public void shutdown() {

    }

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if(Platform.isFxApplicationThread()) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if(Platform.isFxApplicationThread()) {
            try {
                task.run();
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                task.run();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public Future<?> submit(Runnable task) {
        if(Platform.isFxApplicationThread()) {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<?> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> list = new ArrayList<>();
        for(var e : tasks) {
            list.add(submit(e));
        }
        return list;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException {
        List<Future<T>> list = new ArrayList<>();
        for(var e : tasks) {
            list.add(submit(e));
        }
        return list;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        if(Platform.isFxApplicationThread()) {
            command.run();
            return;
        }
        Platform.runLater(command);
    }
}
