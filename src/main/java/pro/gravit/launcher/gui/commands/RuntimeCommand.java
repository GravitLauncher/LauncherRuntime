package pro.gravit.launcher.gui.commands;

import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.commands.runtime.*;
import pro.gravit.utils.command.Command;

public class RuntimeCommand extends Command {
    private final JavaFXApplication application;

    public RuntimeCommand(JavaFXApplication application) {
        this.application = application;
        this.childCommands.put("dialog", new DialogCommand(application.messageManager));
        this.childCommands.put("warp", new WarpCommand(application));
        this.childCommands.put("reload", new ReloadCommand(application));
        this.childCommands.put("notify", new NotifyCommand(application.messageManager));
        this.childCommands.put("theme", new ThemeCommand(application));
        this.childCommands.put("info", new InfoCommand(application));
        this.childCommands.put("getsize", new GetSizeCommand(application));
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
        invokeSubcommands(args);
    }
}