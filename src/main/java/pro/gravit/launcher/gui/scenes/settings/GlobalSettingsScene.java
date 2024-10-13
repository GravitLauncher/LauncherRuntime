package pro.gravit.launcher.gui.scenes.settings;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.stage.DirectoryChooser;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.settings.components.LanguageSelectorComponent;
import pro.gravit.launcher.gui.scenes.settings.components.ThemeSelectorComponent;
import pro.gravit.launcher.gui.stage.ConsoleStage;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class GlobalSettingsScene extends BaseSettingsScene {
    private ThemeSelectorComponent themeSelector;
    private LanguageSelectorComponent languageSelectorComponent;
    public GlobalSettingsScene(JavaFXApplication application) {
        super("scenes/settings/globalsettings.fxml", application);
    }

    @Override
    public String getName() {
        return "globalsettings";
    }

    @Override
    protected void doInit() {
        super.doInit();
        themeSelector = new ThemeSelectorComponent(application, componentList);
        languageSelectorComponent = new LanguageSelectorComponent(application, componentList);
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#console").setOnAction((e) -> {
            try {
                if (application.gui.consoleStage == null) application.gui.consoleStage = new ConsoleStage(application);
                if (application.gui.consoleStage.isNullScene())
                    application.gui.consoleStage.setScene(application.gui.consoleScene, true);
                application.gui.consoleStage.show();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        Hyperlink updateDirLink = LookupHelper.lookup(componentList, "#folder", "#path");
        String directoryUpdates = DirBridge.dirUpdates.toAbsolutePath().toString();
        updateDirLink.setText(directoryUpdates);
        if (updateDirLink.getTooltip() != null) {
            updateDirLink.getTooltip().setText(directoryUpdates);
        }
        updateDirLink.setOnAction((e) -> application.openURL(directoryUpdates));
        LookupHelper.<ButtonBase>lookup(componentList, "#changeDir").setOnAction((e) -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(application.getTranslation("runtime.scenes.settings.dirTitle"));
            directoryChooser.setInitialDirectory(DirBridge.dir.toFile());
            File choose = directoryChooser.showDialog(application.getMainStage().getStage());
            if (choose == null) return;
            Path newDir = choose.toPath().toAbsolutePath();
            try {
                DirBridge.move(newDir);
            } catch (IOException ex) {
                errorHandle(ex);
            }
            application.runtimeSettings.updatesDirPath = newDir.toString();
            application.runtimeSettings.updatesDir = newDir;
            String oldDir = DirBridge.dirUpdates.toString();
            DirBridge.dirUpdates = newDir;
            for (ClientProfile profile : application.profilesService.getProfiles()) {
                RuntimeSettings.ProfileSettings settings = application.getProfileSettings(profile);
                if (settings.javaPath != null && settings.javaPath.startsWith(oldDir)) {
                    settings.javaPath = newDir.toString().concat(settings.javaPath.substring(oldDir.length()));
                }
            }
            application.javaService.update();
            updateDirLink.setText(application.runtimeSettings.updatesDirPath);
        });
        LookupHelper.<ButtonBase>lookupIfPossible(layout, "#deleteDir").ifPresent(a -> a.setOnAction(
                (e) -> application.messageManager.showApplyDialog(
                        application.getTranslation("runtime.scenes.settings.deletedir.header"),
                        application.getTranslation("runtime.scenes.settings.deletedir.description"), () -> {
                            LogHelper.debug("Delete dir: %s", DirBridge.dirUpdates);
                            try {
                                IOHelper.deleteDir(DirBridge.dirUpdates, false);
                            } catch (IOException ex) {
                                LogHelper.error(ex);
                                application.messageManager.createNotification(
                                        application.getTranslation("runtime.scenes.settings.deletedir.fail.header"),
                                        application.getTranslation(
                                                "runtime.scenes.settings.deletedir.fail.description"));
                            }
                        }, () -> {}, true)));
        LookupHelper.<ButtonBase>lookupIfPossible(layout, "#back").ifPresent(a -> a.setOnAction((e) -> {
            try {
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        RuntimeSettings.GlobalSettings settings = application.runtimeSettings.globalSettings;
        add("PrismVSync", settings.prismVSync, (value) -> settings.prismVSync = value, false);
        add("DebugAllClients", settings.debugAllClients, (value) -> settings.debugAllClients = value, false);
    }
}
