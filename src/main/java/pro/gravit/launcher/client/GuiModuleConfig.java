package pro.gravit.launcher.client;

import pro.gravit.launcher.LauncherInject;

public class GuiModuleConfig {
    @LauncherInject(value = "modules.javaruntime.createaccounturl")
    public String createAccountURL;
    @LauncherInject(value = "modules.javaruntime.forgotpassurl")
    public String forgotPassURL;
    @LauncherInject(value = "modules.javaruntime.hastebinserver")
    public String hastebinServer;

    public static Object getDefault() {
        GuiModuleConfig config = new GuiModuleConfig();
        config.createAccountURL = "https://gravit.pro/createAccount.php";
        config.forgotPassURL = "https://gravit.pro/fogotPass.php";
        config.hastebinServer = "https://hasteb.in";
        return config;
    }
}
