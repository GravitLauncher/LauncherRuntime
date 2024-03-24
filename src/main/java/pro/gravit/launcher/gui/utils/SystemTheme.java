package pro.gravit.launcher.gui.utils;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import pro.gravit.launcher.gui.config.RuntimeSettings;

public class SystemTheme {
    // Available only in JavaFX 22+
    public static RuntimeSettings.LAUNCHER_THEME getSystemTheme() {
        if (Platform.getPreferences().getColorScheme() == ColorScheme.DARK) {
            return RuntimeSettings.LAUNCHER_THEME.DARK;
        }
        return RuntimeSettings.LAUNCHER_THEME.COMMON;
    }
}
