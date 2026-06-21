package pro.gravit.launcher.gui.core.config;

import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.core.LauncherNetworkAPI;
import pro.gravit.launcher.core.api.method.AuthMethodPassword;
import pro.gravit.launcher.core.backend.UserSettings;
import pro.gravit.launcher.gui.core.utils.SystemTheme;
import pro.gravit.launcher.runtime.client.DirBridge;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class RuntimeSettings extends UserSettings {
    public static final LAUNCHER_LOCALE DEFAULT_LOCALE = LAUNCHER_LOCALE.RUSSIAN;
    public transient Path updatesDir;
    @LauncherNetworkAPI
    public boolean autoAuth;
    @LauncherNetworkAPI
    public String updatesDirPath;
    @LauncherNetworkAPI
    public UUID lastProfile;
    @LauncherNetworkAPI
    public volatile LAUNCHER_LOCALE locale;
    @LauncherNetworkAPI
    public volatile LAUNCHER_THEME theme = LAUNCHER_THEME.COMMON;
    @LauncherNetworkAPI
    public List<ClientProfile> profiles;
    @LauncherNetworkAPI
    public GlobalSettings globalSettings = GlobalSettings.makeGlobalSettings(new GuiModuleConfig());

    public static RuntimeSettings getDefault(GuiModuleConfig config) {
        RuntimeSettings runtimeSettings = new RuntimeSettings();
        runtimeSettings.autoAuth = false;
        runtimeSettings.updatesDir = DirBridge.defaultUpdatesDir;
        runtimeSettings.locale = config.locale == null
                ? LAUNCHER_LOCALE.RUSSIAN
                : LAUNCHER_LOCALE.valueOf(config.locale);
        runtimeSettings.theme = null;
        return runtimeSettings;
    }

    public void apply() {
        if (updatesDirPath != null) updatesDir = Paths.get(updatesDirPath);
    }

    public enum LAUNCHER_LOCALE {
        @LauncherNetworkAPI RUSSIAN("ru", "Русский"),
        @LauncherNetworkAPI BELARUSIAN("be", "Беларуская"),
        @LauncherNetworkAPI UKRAINIAN("uk", "Українська"),
        @LauncherNetworkAPI POLISH("pl", "Polska"),
        @LauncherNetworkAPI ENGLISH("en", "English");
        public final String name;
        public final String displayName;

        LAUNCHER_LOCALE(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
    }

    public enum LAUNCHER_THEME {
        @LauncherNetworkAPI COMMON(null, "default"),
        @LauncherNetworkAPI DARK("dark", "dark");
        public final String name;
        public final String displayName;

        LAUNCHER_THEME(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
    }

    public static class GlobalSettings {
        @LauncherNetworkAPI
        public boolean prismVSync = true;
        @LauncherNetworkAPI
        public boolean debugAllClients = true;

        public static GlobalSettings makeGlobalSettings(GuiModuleConfig config) {
            GlobalSettings settings = new GlobalSettings();
            settings.debugAllClients = !config.disableDebugByDefault;
            return settings;
        }
    }
}
