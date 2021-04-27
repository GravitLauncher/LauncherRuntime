package pro.gravit.launcher.client.gui.impl;

import javafx.application.Platform;
import pro.gravit.utils.helper.LogHelper;

import java.util.concurrent.CompletableFuture;

public class ContextHelper {
    private final AbstractVisualComponent pane;

    ContextHelper(AbstractVisualComponent pane) {
        this.pane = pane;
    }

    public static<T> CompletableFuture<T> runInFxThreadStatic(GuiExceptionCallback<T> callback) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if(Platform.isFxApplicationThread()) {
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

    public<T> T runCallback(GuiExceptionCallback<T> callback) {
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
        if(Platform.isFxApplicationThread()) {
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

    public final<T> CompletableFuture<T> runInFxThread(GuiExceptionCallback<T> callback) {
        return runInFxThreadStatic(callback).exceptionally((t) -> {
            errorHandling(t);
            return null;
        });
    }

    public final CompletableFuture<Void> runInFxThread(GuiExceptionRunnable callback) {
        return runInFxThreadStatic(callback).exceptionally((t) -> {
            errorHandling(t);
            return null;
        });
    }

    final void errorHandling(Throwable e) {
        if (pane != null) {
            pane.errorHandle(e);
            /*
            AbstractOverlay currentOverlay = scene.getCurrentOverlay();
            if (currentOverlay != null) {
                currentOverlay.errorHandle(e);
                scene.hideOverlay(2000, null);
            } else {
                scene.errorHandle(e);
            }*/
        }
    }

    public interface GuiExceptionCallback<T> {
        T call() throws Throwable;
    }

    public interface GuiExceptionRunnable {
        void call() throws Throwable;
    }
}
