package pro.gravit.launcher.client.gui.scenes.settings;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import oshi.SystemInfo;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerButtonComponent;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class SettingsScene extends AbstractScene {
    private Pane componentList;
    private Label ramLabel;
    private Slider ramSlider;
    private RuntimeSettings.ProfileSettingsView profileSettings;
    private JavaSelectorComponent javaSelector;

    public SettingsScene(JavaFXApplication application) {
        super("scenes/settings/settings.fxml", application);
    }

    @Override
    protected void doInit() {
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#settingslist").getContent();
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#console").setOnAction((e) -> {
            try {
                if (application.gui.consoleStage == null)
                    application.gui.consoleStage = new ConsoleStage(application);
                if (application.gui.consoleStage.isNullScene())
                    application.gui.consoleStage.setScene(application.gui.consoleScene);
                application.gui.consoleStage.show();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });

        ramSlider = LookupHelper.lookup(layout, "#ramSlider");
        ramLabel = LookupHelper.lookup(layout, "#ramLabel");
        try {
            SystemInfo systemInfo = new SystemInfo();
            ramSlider.setMax(systemInfo.getHardware().getMemory().getTotal() >> 20);
        } catch (Throwable e) {
            ramSlider.setMax(2048);
        }

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(1);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                return String.format("%.0fG", object / 1024);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        Hyperlink updateDirLink = LookupHelper.lookup(layout, "#folder", "#path");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
        updateDirLink.setOnAction((e) -> {
            application.openURL(DirBridge.dirUpdates.toAbsolutePath().toString());
        });
        LookupHelper.<ButtonBase>lookup(layout, "#changeDir").setOnAction((e) -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Сменить директорию загрузок");
            directoryChooser.setInitialDirectory(DirBridge.dir.toFile());
            File choose = directoryChooser.showDialog(application.getMainStage().stage);
            if (choose == null)
                return;
            Path newDir = choose.toPath().toAbsolutePath();
            try {
                DirBridge.move(newDir);
            } catch (IOException ex) {
                errorHandle(ex);
            }
            application.runtimeSettings.updatesDirPath = newDir.toString();
            application.runtimeSettings.updatesDir = newDir;
            updateDirLink.setText(application.runtimeSettings.updatesDirPath);
        });
        LookupHelper.<ButtonBase>lookupIfPossible(layout, "#deleteDir").ifPresent(a -> a.setOnAction((e) ->
                application.messageManager.showApplyDialog(application.getTranslation("runtime.scenes.settings.deletedir.header"),
                        application.getTranslation("runtime.scenes.settings.deletedir.description"),
                        () -> {
                            LogHelper.debug("Delete dir: %s", DirBridge.dirUpdates);
                            try {
                                IOHelper.deleteDir(DirBridge.dirUpdates, false);
                            } catch (IOException ex) {
                                LogHelper.error(ex);
                                application.messageManager.createNotification(application.getTranslation("runtime.scenes.settings.deletedir.fail.header"),
                                        application.getTranslation("runtime.scenes.settings.deletedir.fail.description"));
                            }
                        }, () -> {
                        }, true)));
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#back").ifPresent(a -> a.setOnAction((e) ->{
            try {
                profileSettings = null;
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        reset();
    }

    @Override
    public void reset() {
        profileSettings = new RuntimeSettings.ProfileSettingsView(application.getProfileSettings());
        javaSelector = new JavaSelectorComponent(layout, profileSettings, application.stateService.getProfile());
        ramSlider.setValue(profileSettings.ram);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            profileSettings.ram = newValue.intValue();
            updateRamLabel();
        });
        updateRamLabel();
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.stateService.getProfile();
        ServerButtonComponent serverButton = ServerMenuScene.getServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(null, (e) -> {
            try {
                profileSettings.apply();
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        serverButton.enableResetButton(null, (e) -> {
            //TODO
        });
        componentList.getChildren().clear();
        add("Debug", profileSettings.debug, (value) -> profileSettings.debug = value);
        add("AutoEnter", profileSettings.autoEnter, (value) -> profileSettings.autoEnter = value);
        add("Fullscreen", profileSettings.fullScreen, (value) -> profileSettings.fullScreen = value);
        if(application.securityService.isMayBeDownloadJava() && application.guiModuleConfig.enableDownloadJava && application.guiModuleConfig.userDisableDownloadJava)
        {
            add("DisableJavaDownload", application.runtimeSettings.disableJavaDownload, (value) -> application.runtimeSettings.disableJavaDownload = value);
        }

    }

    @Override
    public String getName() {
        return "settings";
    }

    public void add(String languageName, boolean value, Consumer<Boolean> onChanged) {
        String nameKey = String.format("runtime.scenes.settings.properties.%s.name", languageName.toLowerCase());
        String descriptionKey = String.format("runtime.scenes.settings.properties.%s.description", languageName.toLowerCase());
        add(application.getTranslation(nameKey, languageName), application.getTranslation(descriptionKey, languageName), value, onChanged);
    }

    public void add(String name, String description, boolean value, Consumer<Boolean> onChanged) {
        FlowPane container = new FlowPane();
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(value);
        checkBox.setText(name);
        Text desc = new Text();
        desc.setText(description);
        container.getChildren().add(checkBox);
        container.getChildren().add(desc);

        checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        componentList.getChildren().add(container);
        container.getStyleClass().add("settings-container");
        checkBox.getStyleClass().add("settings-checkbox");
        desc.getStyleClass().add("settings-description");
        FlowPane.setMargin(desc, new Insets(0, 0, 0, 30));
    }
    public void updateRamLabel()
    {
        ramLabel.setText(profileSettings.ram == 0 ? "Auto" : Integer.toString(profileSettings.ram).concat(" MiB"));
    }
}
