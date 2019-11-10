package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.managers.SettingsManager;

public class StdSettingsManager extends SettingsManager {
    @Override
    public NewLauncherSettings getDefaultConfig() {
        NewLauncherSettings newLauncherSettings = new NewLauncherSettings();
        newLauncherSettings.updatesDir = DirBridge.defaultUpdatesDir;
        newLauncherSettings.autoEnter = false;
        newLauncherSettings.fullScreen = false;
        newLauncherSettings.ram = 1024;
        newLauncherSettings.featureStore = true;
        return newLauncherSettings;
    }
}
