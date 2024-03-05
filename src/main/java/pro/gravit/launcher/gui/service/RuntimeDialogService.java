package pro.gravit.launcher.gui.service;

import pro.gravit.launcher.client.api.DialogService;
import pro.gravit.launcher.gui.impl.MessageManager;
import pro.gravit.launcher.base.events.NotificationEvent;

import java.util.function.Consumer;

public class RuntimeDialogService implements DialogService.DialogServiceNotificationImplementation, DialogService.DialogServiceImplementation {
    private final MessageManager messageManager;

    public RuntimeDialogService(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public void showDialog(String header, String text, Runnable onApplyCallback, Runnable onCloseCallback) {
        messageManager.showDialog(header, text, onApplyCallback, onCloseCallback, false);
    }

    @Override
    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback) {
        messageManager.showApplyDialog(header, text, onApplyCallback, onDenyCallback, false);
    }

    @Override
    public void showApplyDialog(String header, String text, Runnable onApplyCallback, Runnable onDenyCallback,
            Runnable onCloseCallback) {
        messageManager.showApplyDialog(header, text, onApplyCallback, onDenyCallback, onCloseCallback, false);
    }

    @Override
    public void showTextDialog(String header, Consumer<String> onApplyCallback, Runnable onCloseCallback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createNotification(NotificationEvent.NotificationType type, String head, String message) {
        messageManager.createNotification(head, message);
    }
}
