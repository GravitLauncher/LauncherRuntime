package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.events.NotificationEvent;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.utils.helper.LogHelper;

import java.util.function.Consumer;

public class GuiEventHandler implements Consumer<WebSocketEvent> {
    private final JavaFXApplication application;

    public GuiEventHandler(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public void accept(WebSocketEvent event) {
        LogHelper.dev("Processing event %s", event.getType());
        try {
            if(event instanceof NotificationEvent)
            {
                NotificationEvent e = (NotificationEvent) event;
                application.messageManager.createNotification(e.head, e.message);
            }
            if(event instanceof AuthRequestEvent)
            {
                boolean isNextScene = application.getCurrentScene() instanceof LoginScene;
                application.runtimeStateMachine.setAuthResult((AuthRequestEvent) event);
                if(isNextScene)
                {
                    ((LoginScene)application.getCurrentScene()).onGetProfiles();
                }

            }
        } catch (Throwable e)
        {
            LogHelper.error(e);
        }
    }
}
