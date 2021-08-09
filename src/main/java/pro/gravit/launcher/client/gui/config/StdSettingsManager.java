package pro.gravit.launcher.client.gui.config;

import pro.gravit.launcher.NewLauncherSettings;
import pro.gravit.launcher.client.JavaRuntimeModule;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.managers.SettingsManager;

public class StdSettingsManager extends SettingsManager {

    @Override
    public NewLauncherSettings getDefaultConfig() {
        NewLauncherSettings newLauncherSettings = new NewLauncherSettings();
        newLauncherSettings.userSettings.put(JavaRuntimeModule.RUNTIME_NAME, RuntimeSettings.getDefault());
        return newLauncherSettings;
    }
}
