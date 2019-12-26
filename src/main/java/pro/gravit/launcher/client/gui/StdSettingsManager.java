package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.managers.SettingsManager;

public class StdSettingsManager extends SettingsManager {
    @Override
    public NewLauncherSettings getDefaultConfig() {
        NewLauncherSettings newLauncherSettings = new NewLauncherSettings();
        newLauncherSettings.userSettings.put("stdruntime", RuntimeSettings.getDefault());
        return newLauncherSettings;
    }
}
