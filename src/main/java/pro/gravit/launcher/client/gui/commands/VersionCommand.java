package pro.gravit.launcher.client.gui.commands;

import pro.gravit.launcher.client.gui.scenes.console.ConsoleScene;
import pro.gravit.utils.command.Command;
import pro.gravit.utils.helper.LogHelper;

public class VersionCommand extends Command {
    @Override
    public String getArgsDescription() {
        return "print version information";
    }

    @Override
    public String getUsageDescription() {
        return "[]";
    }

    @Override
    public void invoke(String... args) {
        LogHelper.info(ConsoleScene.getLauncherInfo());
        LogHelper.info("JDK Path: %s", System.getProperty("java.home", "UNKNOWN"));
    }
}
