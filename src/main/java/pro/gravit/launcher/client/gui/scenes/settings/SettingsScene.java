package pro.gravit.launcher.client.gui.scenes.settings;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import oshi.SystemInfo;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerButton;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;
import pro.gravit.launcher.client.gui.utils.SystemMemory;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.function.Consumer;

public class SettingsScene extends AbstractScene {

    private final static long MAX_JAVA_MEMORY_X64 = 32 * 1024;
    private final static long MAX_JAVA_MEMORY_X32 = 1536;
    private Pane componentList;
    private Pane settingsList;
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
        settingsList = LookupHelper.lookup(componentList, "#settings-list");
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#console").setOnAction((e) -> {
            try {
                if (application.gui.consoleStage == null) application.gui.consoleStage = new ConsoleStage(application);
                if (application.gui.consoleStage.isNullScene())
                    application.gui.consoleStage.setScene(application.gui.consoleScene);
                application.gui.consoleStage.show();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });

        ramSlider = LookupHelper.lookup(componentList, "#ramSlider");
        ramLabel = LookupHelper.lookup(componentList, "#ramLabel");
        long maxSystemMemory;
        try {
            SystemInfo systemInfo = new SystemInfo();
            maxSystemMemory = (systemInfo.getHardware().getMemory().getTotal() >> 20);
        } catch (Throwable ignored) {
            try {
                maxSystemMemory = SystemMemory.getPhysicalMemorySize();
            } catch (Throwable ignored1) {
                maxSystemMemory = 2048;
            }
        }
        ramSlider.setMax(Math.min(maxSystemMemory, getJavaMaxMemory()));

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(1);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double object) {
                return "%.0fG".formatted(object / 1024);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        Hyperlink updateDirLink = LookupHelper.lookup(componentList, "#folder", "#path");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
        updateDirLink.setOnAction((e) -> application.openURL(DirBridge.dirUpdates.toAbsolutePath().toString()));
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
            javaSelector.reset();
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
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#back").ifPresent(a -> a.setOnAction((e) -> {
            try {
                profileSettings = null;
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        reset();
    }

    private long getJavaMaxMemory() {
        if (application.javaService.isArchAvailable(JVMHelper.ARCH.X86_64) || application.javaService.isArchAvailable(
                JVMHelper.ARCH.ARM64)) {
            return MAX_JAVA_MEMORY_X64;
        }
        return MAX_JAVA_MEMORY_X32;
    }

    @Override
    public void reset() {
        profileSettings = new RuntimeSettings.ProfileSettingsView(application.getProfileSettings());
        javaSelector = new JavaSelectorComponent(application.javaService, componentList, profileSettings,
                                                 application.profilesService.getProfile());
        ramSlider.setValue(profileSettings.ram);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            profileSettings.ram = newValue.intValue();
            updateRamLabel();
        });
        updateRamLabel();
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.profilesService.getProfile();
        ServerButton serverButton = ServerMenuScene.getServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(null, (e) -> {
            try {
                profileSettings.apply();
                application.triggerManager.process(profile, application.profilesService.getOptionalView());
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        serverButton.enableResetButton(null, (e) -> reset());
        settingsList.getChildren().clear();
        add("Debug", profileSettings.debug, (value) -> profileSettings.debug = value);
        add("AutoEnter", profileSettings.autoEnter, (value) -> profileSettings.autoEnter = value);
        add("Fullscreen", profileSettings.fullScreen, (value) -> profileSettings.fullScreen = value);
        if(JVMHelper.OS_TYPE == JVMHelper.OS.LINUX) {
            add("WaylandSupport", profileSettings.waylandSupport, (value) -> profileSettings.waylandSupport = value);
        }
    }

    @Override
    public String getName() {
        return "settings";
    }

    public void add(String languageName, boolean value, Consumer<Boolean> onChanged) {
        String nameKey = "runtime.scenes.settings.properties.%s.name".formatted(languageName.toLowerCase());
        String descriptionKey = "runtime.scenes.settings.properties.%s.description".formatted(
                languageName.toLowerCase());
        add(application.getTranslation(nameKey, languageName), application.getTranslation(descriptionKey, languageName),
            value, onChanged);
    }

    public void add(String name, String description, boolean value, Consumer<Boolean> onChanged) {
        HBox hBox = new HBox();
        CheckBox checkBox = new CheckBox();
        Label header = new Label();
        Label label = new Label();
        VBox vBox = new VBox();
        hBox.getStyleClass().add("settings-container");
        checkBox.getStyleClass().add("settings-checkbox");
        header.getStyleClass().add("settings-label-header");
        label.getStyleClass().add("settings-label");
        checkBox.setSelected(value);
        checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        header.setText(name);
        label.setText(description);
        label.setWrapText(true);
        vBox.getChildren().add(header);
        vBox.getChildren().add(label);
        hBox.getChildren().add(checkBox);
        hBox.getChildren().add(vBox);
        settingsList.getChildren().add(hBox);
    }

    public void updateRamLabel() {
        ramLabel.setText(profileSettings.ram == 0
                                 ? application.getTranslation("runtime.scenes.settings.ramAuto")
                                 : MessageFormat.format(application.getTranslation("runtime.scenes.settings.ram"),
                                                        profileSettings.ram));
    }
}
