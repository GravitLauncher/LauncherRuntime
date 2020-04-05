package pro.gravit.launcher.client.gui.raw;

import javafx.application.Platform;
import pro.gravit.utils.helper.LogHelper;

public class ContextHelper {
    private final AbstractScene scene;

    ContextHelper(AbstractScene scene) {
        this.scene = scene;
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
        LogHelper.error(e);
        if (scene != null) {
            AbstractOverlay currentOverlay = scene.getCurrentOverlay();
            if (currentOverlay != null) {
                currentOverlay.errorHandle(e);
                scene.hideOverlay(2000, null);
            }
        }
    }

    public interface GuiExceptionCallback {
        void call() throws Throwable;
    }
}
