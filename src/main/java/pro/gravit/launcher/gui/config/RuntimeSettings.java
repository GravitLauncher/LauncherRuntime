package pro.gravit.launcher.gui.config;
import pro.gravit.launcher.gui.service.JavaService;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.launcher.runtime.client.UserSettings;
import pro.gravit.launcher.core.LauncherNetworkAPI;
import pro.gravit.launcher.base.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.base.request.auth.AuthRequest;
import pro.gravit.utils.helper.JavaHelper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RuntimeSettings extends UserSettings {
    public static final LAUNCHER_LOCALE DEFAULT_LOCALE = LAUNCHER_LOCALE.RUSSIAN;
    public transient Path updatesDir;
    @LauncherNetworkAPI
    public String login;
    @LauncherNetworkAPI
    public AuthRequest.AuthPasswordInterface password;
    @LauncherNetworkAPI
    @Deprecated
    public byte[] encryptedPassword;
    @LauncherNetworkAPI
    public boolean autoAuth;
    @LauncherNetworkAPI
    public GetAvailabilityAuthRequestEvent.AuthAvailability lastAuth;
    @LauncherNetworkAPI
    public String updatesDirPath;
    @LauncherNetworkAPI
    public UUID lastProfile;
    @LauncherNetworkAPI
    public LAUNCHER_LOCALE locale;
    @LauncherNetworkAPI
    public String oauthAccessToken;
    @LauncherNetworkAPI
    public String oauthRefreshToken;
    @LauncherNetworkAPI
    public long oauthExpire;
    @LauncherNetworkAPI
    public String theme;
    @LauncherNetworkAPI
    public Map<UUID, ProfileSettings> profileSettings = new HashMap<>();
    @LauncherNetworkAPI
    public List<ClientProfile> profiles;

    public static RuntimeSettings getDefault(GuiModuleConfig config) {
        RuntimeSettings runtimeSettings = new RuntimeSettings();
        runtimeSettings.autoAuth = false;
        runtimeSettings.updatesDir = DirBridge.defaultUpdatesDir;
        runtimeSettings.locale = config.locale == null
                ? LAUNCHER_LOCALE.RUSSIAN
                : LAUNCHER_LOCALE.valueOf(config.locale);
        return runtimeSettings;
    }

    public void apply() {
        if (updatesDirPath != null) updatesDir = Paths.get(updatesDirPath);
    }

    public enum LAUNCHER_LOCALE {
        @LauncherNetworkAPI RUSSIAN("ru", "Русский"), @LauncherNetworkAPI ENGLISH("en", "English");
        public final String name;
        public final String displayName;

        LAUNCHER_LOCALE(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
    }

    public static class ProfileSettings {
        @LauncherNetworkAPI
        public int ram;
        @LauncherNetworkAPI
        public boolean debug;
        @LauncherNetworkAPI
        public boolean fullScreen;
        @LauncherNetworkAPI
        public boolean autoEnter;
        @LauncherNetworkAPI
        public String javaPath;
        @LauncherNetworkAPI
        public boolean waylandSupport;

        public static ProfileSettings getDefault(JavaService javaService, ClientProfile profile) {
            ProfileSettings settings = new ProfileSettings();
            ClientProfile.ProfileDefaultSettings defaultSettings = profile.getSettings();
            settings.ram = defaultSettings.ram;
            settings.autoEnter = defaultSettings.autoEnter;
            settings.fullScreen = defaultSettings.fullScreen;
            JavaHelper.JavaVersion version = javaService.getRecommendJavaVersion(profile);
            if (version != null) {
                settings.javaPath = version.jvmDir.toString();
            }
            return settings;
        }

        public ProfileSettings() {

        }
    }

    public static class ProfileSettingsView {
        private transient final ProfileSettings settings;
        public int ram;
        public boolean debug;
        public boolean fullScreen;
        public boolean autoEnter;
        public String javaPath;
        public boolean waylandSupport;

        public ProfileSettingsView(ProfileSettings settings) {
            ram = settings.ram;
            debug = settings.debug;
            fullScreen = settings.fullScreen;
            autoEnter = settings.autoEnter;
            javaPath = settings.javaPath;
            waylandSupport = settings.waylandSupport;
            this.settings = settings;
        }

        public void apply() {
            settings.ram = ram;
            settings.debug = debug;
            settings.autoEnter = autoEnter;
            settings.fullScreen = fullScreen;
            settings.javaPath = javaPath;
            settings.waylandSupport = waylandSupport;
        }
    }
}