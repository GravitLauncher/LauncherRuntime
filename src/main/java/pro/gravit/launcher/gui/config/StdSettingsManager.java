package pro.gravit.launcher.gui.config;

import pro.gravit.launcher.gui.JavaRuntimeModule;
import pro.gravit.launcher.runtime.NewLauncherSettings;
import pro.gravit.launcher.runtime.managers.SettingsManager;

public class StdSettingsManager extends SettingsManager {

    @Override
    public NewLauncherSettings getDefaultConfig() {
        NewLauncherSettings newLauncherSettings = new NewLauncherSettings();
        newLauncherSettings.userSettings.put(JavaRuntimeModule.RUNTIME_NAME,
                                             RuntimeSettings.getDefault(new GuiModuleConfig()));
        return newLauncherSettings;
    }
}
