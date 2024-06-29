package pro.gravit.launcher.gui.basic;

import pro.gravit.launcher.gui.scenes.LoginScene;
import pro.gravit.launcher.gui.scenes.ServerInfoScene;
import pro.gravit.launcher.gui.scenes.ServerMenuScene;

public class Scenes {
    public static volatile LoginScene LOGIN;
    public static volatile ServerMenuScene SERVER_MENU;
    public static volatile ServerInfoScene SERVER_INFO;

    public static void init() {
        LOGIN = new LoginScene();
        SERVER_MENU = new ServerMenuScene();
        SERVER_INFO = new ServerInfoScene();
    }
}
