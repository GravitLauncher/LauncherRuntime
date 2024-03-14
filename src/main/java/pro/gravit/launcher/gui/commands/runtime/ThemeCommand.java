package pro.gravit.launcher.gui.commands.runtime;

import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.utils.command.Command;

public class ThemeCommand extends Command {
    private final JavaFXApplication application;

    public ThemeCommand(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public String getArgsDescription() {
        return "[theme]";
    }

    @Override
    public String getUsageDescription() {
        return "Change theme and reload";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 1);
        application.runtimeSettings.theme = RuntimeSettings.LAUNCHER_THEME.valueOf(args[0]);
        application.gui.reload();
    }
}
