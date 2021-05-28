package pro.gravit.launcher.client.gui.impl;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.events.NotificationEvent;
import pro.gravit.launcher.events.RequestEvent;
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
        if( event instanceof RequestEvent)
        {
            if(!((RequestEvent) event).requestUUID.equals(RequestEvent.eventUUID))
                return false;
        }
        try {
            if (event instanceof NotificationEvent) {
                NotificationEvent e = (NotificationEvent) event;
                ContextHelper.runInFxThreadStatic(() -> application.messageManager.createNotification(e.head, e.message));
            } else if (event instanceof AuthRequestEvent) {
                boolean isNextScene = application.getCurrentScene() instanceof LoginScene;
                LogHelper.dev("Receive auth event. Send next scene %s", isNextScene ? "true" : "false");
                application.stateService.setAuthResult(null, (AuthRequestEvent) event);
                if (isNextScene && ((LoginScene) application.getCurrentScene()).isLoginStarted)
                    ((LoginScene) application.getCurrentScene()).onGetProfiles();
            }
        } catch (Throwable e) {
            LogHelper.error(e);
        }
        return false;
    }
}
