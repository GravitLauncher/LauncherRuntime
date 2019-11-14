package pro.gravit.launcher.client.gui.helper;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import pro.gravit.launcher.client.gui.overlay.AbstractOverlay;
import pro.gravit.launcher.client.gui.overlay.ProcessingOverlay;
import pro.gravit.launcher.client.gui.scene.AbstractScene;
import pro.gravit.utils.helper.LogHelper;

public class ContextHelper {
    private final AbstractScene scene;

    public ContextHelper(AbstractScene scene) {
        this.scene = scene;
    }
    public interface GuiExceptionCallback
    {
        void call() throws Exception;
    }
    public final Runnable runCallback(GuiExceptionCallback callback)
    {
        return () -> {
            try {
                callback.call();
            } catch (Exception ex) {
                errorHandling(ex);
            }
        };
    }
    public final void runInFxThread(GuiExceptionCallback callback)
    {
        Platform.runLater(() -> {
            try {
                callback.call();
            } catch (Exception ex) {
                errorHandling(ex);
            }
        });
    }
    public static void runInFxThreadStatic(GuiExceptionCallback callback)
    {
        Platform.runLater(() -> {
            try {
                callback.call();
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
    }
    public final void errorHandling(Exception e)
    {
        LogHelper.error(e);
        if(scene != null)
        {
            AbstractOverlay currentOverlay = scene.getCurrentOverlay();
            if(currentOverlay != null)
            {
                if(currentOverlay instanceof ProcessingOverlay)
                {
                    ((ProcessingOverlay) currentOverlay).setError(e);
                    scene.hideOverlay(2000, null);
                    return;
                }
                scene.hideOverlay(100, null);
            }
        }
    }
}
