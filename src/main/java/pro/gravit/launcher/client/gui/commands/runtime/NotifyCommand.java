package pro.gravit.launcher.client.gui.commands.runtime;

import pro.gravit.launcher.client.gui.impl.MessageManager;
import pro.gravit.utils.command.Command;

public class NotifyCommand extends Command {
    private final MessageManager messageManager;

    public NotifyCommand(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public String getArgsDescription() {
        return "[header] [message] (launcher/native/default)";
    }

    @Override
    public String getUsageDescription() {
        return "show notify message";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 2);
        boolean isDefault = args.length <= 2 || args[2].equals("default");
        boolean isLauncher = args.length <= 2 || args[2].equals("launcher");
        String header = args[0];
        String message = args[1];
        if (isDefault)
            messageManager.createNotification(header, message);
        else
            messageManager.createNotification(header, message, isLauncher);
    }
}
