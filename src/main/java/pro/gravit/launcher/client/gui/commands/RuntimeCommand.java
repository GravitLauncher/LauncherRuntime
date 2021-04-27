package pro.gravit.launcher.client.gui.commands;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.utils.command.Command;

public class RuntimeCommand extends Command {
    private final JavaFXApplication application;

    public RuntimeCommand(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public String getArgsDescription() {
        return null;
    }

    @Override
    public String getUsageDescription() {
        return null;
    }

    @Override
    public void invoke(String... args) throws Exception {
        application.gui.reload();
    }
}
