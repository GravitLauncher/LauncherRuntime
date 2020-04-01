package pro.gravit.launcher.client;

import pro.gravit.launcher.LauncherInject;

public class GUIModuleConfig {
    @LauncherInject(value = "modules.javaruntime.createaccounturl")
    public String createAccountURL;
    @LauncherInject(value = "modules.javaruntime.forgotpassurl")
    public String forgotPassURL;
    public static Object getDefault()
    {
        GUIModuleConfig config = new GUIModuleConfig();
        config.createAccountURL = "https://gravit.pro/createAccount.php";
        config.forgotPassURL = "https://gravit.pro/fogotPass.php";
        return config;
    }
}
