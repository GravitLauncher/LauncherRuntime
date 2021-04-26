package pro.gravit.launcher.client.gui.impl;

import javafx.application.Platform;
import pro.gravit.utils.helper.LogHelper;

public class ContextHelper {
    private final AbstractVisualComponent pane;

    ContextHelper(AbstractVisualComponent pane) {
        this.pane = pane;
    }

    public static void runInFxThreadStatic(GuiExceptionCallback callback) {
        Platform.runLater(() -> {
            try {
                callback.call();
            } catch (Throwable ex) {
                LogHelper.error(ex);
            }
        });
    }

    public final Runnable runCallback(GuiExceptionCallback callback) {
        return () -> {
            try {
                callback.call();
            } catch (Throwable ex) {
                errorHandling(ex);
            }
        };
    }

    public final void runInFxThread(GuiExceptionCallback callback) {
        Platform.runLater(() -> {
            try {
                callback.call();
            } catch (Throwable ex) {
                errorHandling(ex);
            }
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

    public interface GuiExceptionCallback {
        void call() throws Throwable;
    }
}
