package pro.gravit.launcher.gui.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class FXExecutorService implements ExecutorService {
    private final ContextHelper contextHelper;

    public FXExecutorService(ContextHelper contextHelper) {
        this.contextHelper = contextHelper;
    }

    @Override
    public void shutdown() {
        // None
    }

    @Override
    public List<Runnable> shutdownNow() {
        return new ArrayList<>(0);
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
    public boolean awaitTermination(long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        return contextHelper.runInFxThread(callable::call);
    }

    @Override
    public <T> Future<T> submit(Runnable runnable, T t) {
        return contextHelper.runInFxThread(runnable::run).thenApply((v) -> t);
    }

    @Override
    public Future<?> submit(Runnable runnable) {
        return contextHelper.runInFxThread(runnable::run);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection) {
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection, long l,
            TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <T> T invokeAny(
            Collection<? extends Callable<T>> collection) {
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l,
            TimeUnit timeUnit) {
        return null;
    }

    @Override
    public void execute(Runnable runnable) {
        contextHelper.runInFxThread(runnable::run);
    }
}
