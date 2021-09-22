package pro.gravit.launcher.client.gui.config;

import pro.gravit.launcher.LauncherInject;

import java.util.HashMap;
import java.util.Map;

public class GuiModuleConfig {
    @LauncherInject(value = "modules.javaruntime.createaccounturl")
    public String createAccountURL;
    @LauncherInject(value = "modules.javaruntime.forgotpassurl")
    public String forgotPassURL;
    @LauncherInject(value = "modules.javaruntime.hastebinserver")
    public String hastebinServer;
    @LauncherInject(value = "modules.javaruntime.forcedownloadjava")
    public boolean forceDownloadJava;
    @LauncherInject(value = "modules.javaruntime.javalist")
    public Map<String, String> javaList;

    public static Object getDefault() {
        GuiModuleConfig config = new GuiModuleConfig();
        config.createAccountURL = "https://gravit.pro/createAccount.php";
        config.forgotPassURL = "https://gravit.pro/fogotPass.php";
        config.hastebinServer = "https://hastebin.com";
        config.javaList = new HashMap<>();
        return config;
    }
}
