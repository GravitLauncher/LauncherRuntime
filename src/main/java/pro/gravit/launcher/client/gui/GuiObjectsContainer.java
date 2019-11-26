package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.overlay.ProcessingOverlay;
import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.client.gui.scene.OptionsScene;
import pro.gravit.launcher.client.gui.scene.ServerMenuScene;
import pro.gravit.launcher.client.gui.scene.SettingsScene;

public class GuiObjectsContainer {
    public ProcessingOverlay processingOverlay;
    public ServerMenuScene serverMenuScene;
    public LoginScene loginScene;
    public OptionsScene optionsScene;
    public SettingsScene settingsScene;
    public final ObjectContainer objects = new ObjectContainer();
}
