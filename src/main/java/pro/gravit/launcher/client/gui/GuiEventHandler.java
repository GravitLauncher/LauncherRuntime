package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.events.NotificationEvent;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.websockets.ClientWebSocketService;
import pro.gravit.utils.helper.LogHelper;

public class GuiEventHandler implements ClientWebSocketService.EventHandler {
    private final JavaFXApplication application;

    public GuiEventHandler(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public <T extends WebSocketEvent> boolean eventHandle(T event) {
        LogHelper.dev("Processing event %s", event.getType());
        try {
            if (event instanceof NotificationEvent) {
                NotificationEvent e = (NotificationEvent) event;
                ContextHelper.runInFxThreadStatic(() -> application.messageManager.createNotification(e.head, e.message));
            } else if (event instanceof AuthRequestEvent) {
                boolean isNextScene = application.getCurrentScene() instanceof LoginScene;
                LogHelper.dev("Receive auth event. Send next scene %s", isNextScene ? "true" : "false");
                application.runtimeStateMachine.setAuthResult((AuthRequestEvent) event);
                if (isNextScene && ((LoginScene)application.getCurrentScene()).isStartedLogin) {
                    ((LoginScene) application.getCurrentScene()).onGetProfiles();
                }

            }
        } catch (Throwable e) {
            LogHelper.error(e);
        }
        return false;
    }
}
