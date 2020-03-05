package pro.gravit.launcher.client.gui.commands;

import pro.gravit.launcher.client.gui.scene.ConsoleScene;
import pro.gravit.utils.Version;
import pro.gravit.utils.command.Command;
import pro.gravit.utils.helper.JVMHelper;
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
    public void invoke(String... args) throws Exception {
        LogHelper.info(ConsoleScene.getLauncherInfo());
        LogHelper.info("JDK Path: %s", System.getProperty("java.home", "UNKNOWN"));
    }
}
