package pro.gravit.launcher.client.gui.commands.runtime;

import pro.gravit.utils.command.Command;

public class ShowFxmlCommand extends Command {
    @Override
    public String getArgsDescription() {
        return "[fxmlPath]";
    }

    @Override
    public String getUsageDescription() {
        return "show any fxml without initialize";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 1);
    }
}
