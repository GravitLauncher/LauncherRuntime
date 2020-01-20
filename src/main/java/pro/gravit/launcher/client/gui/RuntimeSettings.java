package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.UserSettings;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class RuntimeSettings extends UserSettings {
    public enum LAUNCHER_LOCALE
    {
        @LauncherNetworkAPI
        RUSSIAN("ru", "Русский"),
        @LauncherNetworkAPI
        ENGLISH("en", "English");
        public final String name;
        public final String displayName;

        LAUNCHER_LOCALE(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
    }
    public static final LAUNCHER_LOCALE DEFAULT_LOCALE = LAUNCHER_LOCALE.RUSSIAN;
    public transient Path updatesDir;
    @LauncherNetworkAPI
    public String login;
    @LauncherNetworkAPI
    public byte[] encryptedPassword;
    @LauncherNetworkAPI
    public GetAvailabilityAuthRequestEvent.AuthAvailability lastAuth;
    @LauncherNetworkAPI
    public String updatesDirPath;
    @LauncherNetworkAPI
    public boolean fullScreen;
    @LauncherNetworkAPI
    public boolean debug;
    @LauncherNetworkAPI
    public boolean autoEnter;
    @LauncherNetworkAPI
    public UUID lastProfile;
    @LauncherNetworkAPI
    public LAUNCHER_LOCALE locale;
    @LauncherNetworkAPI
    public int ram;

    public static RuntimeSettings getDefault() {
        RuntimeSettings runtimeSettings = new RuntimeSettings();
        runtimeSettings.updatesDir = DirBridge.defaultUpdatesDir;
        runtimeSettings.autoEnter = false;
        runtimeSettings.fullScreen = false;
        runtimeSettings.ram = 1024;
        runtimeSettings.locale = DEFAULT_LOCALE;
        return runtimeSettings;
    }

    public void apply() {
        if (updatesDirPath != null)
            updatesDir = Paths.get(updatesDirPath);
    }
}
