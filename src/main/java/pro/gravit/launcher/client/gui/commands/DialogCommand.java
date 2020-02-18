package pro.gravit.launcher.client.gui.commands;

import pro.gravit.launcher.client.gui.raw.MessageManager;
import pro.gravit.utils.command.Command;
import pro.gravit.utils.helper.LogHelper;

public class DialogCommand extends Command {
    private final MessageManager messageManager;

    public DialogCommand(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public String getArgsDescription() {
        return "[header] [message] (dialog/dialogApply/dialogTextInput) (launcher/native/default)";
    }

    @Override
    public String getUsageDescription() {
        return "show test dialog";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 3);
        boolean isDefault = args.length <= 3 || args[3].equals("default");
        boolean isLauncher = args.length <= 3 || args[3].equals("launcher");
        String header = args[0];
        String message = args[1];
        String dialogType = args[2];
        if(dialogType.equals("dialog"))
        {
            messageManager.showDialog(header, message, () -> {
                LogHelper.info("Dialog apply callback");
            }, () -> {
                LogHelper.info("Dialog cancel callback");
            }, isLauncher);
        }
        else if(dialogType.equals("dialogApply"))
        {
            messageManager.showApplyDialog(header, message, () -> {
                LogHelper.info("Dialog apply callback");
            }, () -> {
                LogHelper.info("Dialog deny callback");
            }, () -> {
                LogHelper.info("Dialog close callback");
            }, isLauncher);
        }
        else if(dialogType.equals("dialogTextInput"))
        {
            messageManager.showTextDialog(header, (text) -> {
                LogHelper.info("Dialog apply callback: %s", text);
            }, () -> {
                LogHelper.info("Dialog cancel callback");
            }, isLauncher);
        }
    }
}
