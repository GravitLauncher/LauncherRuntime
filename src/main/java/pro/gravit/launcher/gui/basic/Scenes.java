package pro.gravit.launcher.gui.basic;

import pro.gravit.launcher.gui.scenes.*;

public class Scenes {
    public static volatile LoginScene LOGIN;
    public static volatile ServerMenuScene SERVER_MENU;
    public static volatile ServerInfoScene SERVER_INFO;
    public static volatile UpdateScene UPDATE;
    public static volatile ClientScene CLIENT;
    public static volatile SettingsScene SETTINGS;

    public static void init() {
        LOGIN = new LoginScene();
        SERVER_MENU = new ServerMenuScene();
        SERVER_INFO = new ServerInfoScene();
        UPDATE = new UpdateScene();
        CLIENT = new ClientScene();
        SETTINGS = new SettingsScene();
    }
}
