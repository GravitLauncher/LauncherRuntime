package pro.gravit.launcher.gui.scenes.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.stage.DirectoryChooser;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.settings.components.LanguageSelector;
import pro.gravit.launcher.gui.scenes.settings.components.ThemeSelector;
import pro.gravit.launcher.gui.stage.ConsoleStage;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.utils.helper.IOHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class GlobalSettingsScene extends BaseSettingsScene {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalSettingsScene.class);

    private ThemeSelector themeSelector;
    private LanguageSelector languageSelector;
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
        themeSelector = new ThemeSelector(application, componentList);
        languageSelector = new LanguageSelector(application, componentList);
        LookupHelper.lookup(header, ButtonBase.class, "#controls", "#console").setOnAction((e) -> {
            try {
                if (application.gui.consoleStage == null) application.gui.consoleStage = new ConsoleStage(application);
                if (application.gui.consoleStage.isNullScene())
                    application.gui.consoleStage.setScene(application.gui.consoleScene, true);
                application.gui.consoleStage.show();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        Hyperlink updateDirLink = LookupHelper.lookup(componentList, Hyperlink.class, "#folder", "#path");
        String directoryUpdates = DirBridge.dirUpdates.toAbsolutePath().toString();
        updateDirLink.setText(directoryUpdates);
        if (updateDirLink.getTooltip() != null) {
            updateDirLink.getTooltip().setText(directoryUpdates);
        }
        updateDirLink.setOnAction((e) -> application.openURL(directoryUpdates));
        LookupHelper.lookup(componentList, ButtonBase.class, "#changeDir").setOnAction((e) -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(application.getTranslation("runtime.scenes.settings.dirTitle"));
            directoryChooser.setInitialDirectory(DirBridge.dir.toFile());
            File choose = directoryChooser.showDialog(application.getMainStage().getStage());
            if (choose == null) return;
            Path newDir = choose.toPath().toAbsolutePath();
            if (application.isOperationBusy()) {
                errorHandle(application.getTranslation("runtime.operation.busy", "Finish the current operation first."));
                return;
            }
            application.beginOperation();
            LauncherBackendAPIHolder.getApi().relocateUpdatesDirectory(newDir).whenComplete((value, error) ->
                contextHelper.runInFxThread(() -> {
                    application.endOperation();
                    if (error != null) { errorHandle(error); return; }
                    application.runtimeSettings.updatesDirPath = newDir.toString();
                    application.runtimeSettings.updatesDir = newDir;
                    updateDirLink.setText(newDir.toString());
                    if (updateDirLink.getTooltip() != null) updateDirLink.getTooltip().setText(newDir.toString());
                }));
        });
        LookupHelper.lookupIfPossible(layout, ButtonBase.class, "#deleteDir").ifPresent(a -> a.setOnAction(
                (e) -> application.messageManager.showApplyDialog(
                        application.getTranslation("runtime.scenes.settings.deletedir.header"),
                        application.getTranslation("runtime.scenes.settings.deletedir.description"), () -> {
                            if (application.isOperationBusy()) {
                                errorHandle(application.getTranslation("runtime.operation.busy", "Finish the current operation first."));
                                return;
                            }
                            application.beginOperation();
                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try { IOHelper.deleteDir(DirBridge.dirUpdates, false); }
                                catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
                            }, application.workers).whenComplete((value, error) -> contextHelper.runInFxThread(() -> {
                                application.endOperation();
                                if (error != null) errorHandle(error);
                            }));
                        }, () -> {}, true)));
        LookupHelper.lookupIfPossible(layout, ButtonBase.class, "#back").ifPresent(a -> a.setOnAction((e) -> {
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