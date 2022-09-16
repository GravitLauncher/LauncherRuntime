package pro.gravit.launcher.client.gui.scenes.settings;

import animatefx.animation.FadeIn;
import animatefx.animation.SlideInUp;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
import java.text.MessageFormat;
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
        LookupHelper.<Button>lookup(layout, "#site").setOnMouseClicked((e) ->
                application.openURL("https://github.com/FluffyCuteOwO/VAULT-LAUNCHER-Runtime"));
        LookupHelper.<Button>lookup(layout, "#discord").setOnMouseClicked((e) ->
                application.openURL("https://github.com/FluffyCuteOwO/VAULT-LAUNCHER-Runtime"));
        LookupHelper.<Button>lookup(layout, "#aboutproj").setOnMouseClicked((e) ->
                application.openURL("https://github.com/FluffyCuteOwO/VAULT-LAUNCHER-Runtime"));
        LookupHelper.<ButtonBase>lookup(layout, "#leftpane", "#clientSettings").setOnAction((e) -> {
            try {
                if (application.stateService.getProfile() == null)
                    return;
                switchScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.stateService.getOptionalView());
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        ClientProfile profile1 = application.stateService.getProfile();
        LookupHelper.<ButtonBase>lookup(layout, "#savesettings").setOnAction((e) -> {
            try {
                profileSettings.apply();
                application.triggerManager.process(profile1, application.stateService.getOptionalView());
                super.notificateHandle("Изменения успешно сохраненны!", "Нажмите на уведомление, чтобы скрыть его.");
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
        Hyperlink updateDirLink = LookupHelper.lookup(layout, "#path");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
        updateDirLink.setOnAction((e) -> {
            application.openURL(DirBridge.dirUpdates.toAbsolutePath().toString());
        });
        LookupHelper.<ButtonBase>lookup(layout, "#changeDir").setOnAction((e) -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(application.getTranslation("runtime.scenes.settings.dirTitle"));
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
            String oldDir = DirBridge.dirUpdates.toString();
            DirBridge.dirUpdates = newDir;
            for(ClientProfile profile : application.stateService.getProfiles()) {
                RuntimeSettings.ProfileSettings settings = application.getProfileSettings(profile);
                if(settings.javaPath != null && settings.javaPath.startsWith(oldDir)) {
                    settings.javaPath = newDir.toString().concat(settings.javaPath.substring(oldDir.length()));
                }
            }
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

    @Override
    public void reset() {
        new SlideInUp(LookupHelper.lookup(layout, "#contentbox")).play();
        profileSettings = new RuntimeSettings.ProfileSettingsView(application.getProfileSettings());
        javaSelector = new JavaSelectorComponent(application.javaService, layout, profileSettings, application.stateService.getProfile());
        ramSlider.setValue(profileSettings.ram);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            profileSettings.ram = newValue.intValue();
            updateRamLabel();
        });
        updateRamLabel();
//        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
//        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.stateService.getProfile();
        LookupHelper.<Label>lookup(layout, "#title").setText(profile.getTitle());
//        ServerButtonComponent serverButton = ServerMenuScene.getServerButton(application, profile);
//        serverButton.addTo(serverButtonContainer);
//        serverButton.enableSaveButton(null, (e) -> {
//            try {
//                profileSettings.apply();
//                application.triggerManager.process(profile, application.stateService.getOptionalView());
//                switchScene(application.gui.serverInfoScene);
//            } catch (Exception exception) {
//                errorHandle(exception);
//            }
//        });
//        serverButton.enableResetButton(null, (e) -> {
//            reset();
//        });
        componentList.getChildren().clear();
        add("Debug", profileSettings.debug, (value) -> profileSettings.debug = value);
        add("AutoEnter", profileSettings.autoEnter, (value) -> profileSettings.autoEnter = value);
        add("Fullscreen", profileSettings.fullScreen, (value) -> profileSettings.fullScreen = value);
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
        VBox vBox = new VBox();
        CheckBox checkBox = new CheckBox();
        Pane pane = new Pane();
        pane.setMinWidth(304);
        pane.setMaxWidth(304);
        pane.setMinHeight(60);
        pane.setMaxHeight(60);
        Label label = new Label();
        Label maintext = new Label();
        vBox.getChildren().add(checkBox);
        pane.getChildren().add(label);
        pane.getChildren().add(maintext);
        checkBox.setGraphic(pane);
        VBox.setMargin(vBox, new Insets(0, 0, 10, 0));
        vBox.getStyleClass().add("settings-container");
        checkBox.setSelected(value);
        checkBox.setMinWidth(302);
        checkBox.setMaxWidth(302);
        checkBox.setMinHeight(60);
        checkBox.setMaxHeight(60);
        checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        checkBox.getStyleClass().add("settings-checkbox");
        maintext.setText(name);
        maintext.setWrapText(true);
        maintext.getStyleClass().add("maintext");
        label.setText(description);
        label.setWrapText(true);
        label.getStyleClass().add("descriptiontext");
        componentList.getChildren().add(vBox);
    }

    public void updateRamLabel() {
        ramLabel.setText(profileSettings.ram == 0 ? application.getTranslation("runtime.scenes.settings.ramAuto") : MessageFormat.format(application.getTranslation("runtime.scenes.settings.ram"), profileSettings.ram));
    }
}
