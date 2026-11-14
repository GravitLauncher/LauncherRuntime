package pro.gravit.launcher.gui.core.config;

import pro.gravit.launcher.core.LauncherInject;

public class GuiModuleConfig {
    @LauncherInject(value = "modules.javaruntime.createaccounturl")
    public String createAccountURL;
    @LauncherInject(value = "modules.javaruntime.forgotpassurl")
    public String forgotPassURL;
    @LauncherInject(value = "modules.javaruntime.disabledebugbydefault")
    public boolean disableDebugByDefault;
    @LauncherInject(value = "modules.javaruntime.disabledebugpermissions")
    public boolean disableDebugPermissions;

    @LauncherInject(value = "modules.javaruntime.autoauth")
    public boolean autoAuth;

    @LauncherInject(value = "modules.javaruntime.locale")
    public String locale;


    public static Object getDefault() {
        GuiModuleConfig config = new GuiModuleConfig();
        config.createAccountURL = "https://gravitlauncher.com/createAccount.php";
        config.forgotPassURL = "https://gravitlauncher.com/fogotPass.php";
        config.autoAuth = false;
        config.locale = "RUSSIAN";
        return config;
    }
}
