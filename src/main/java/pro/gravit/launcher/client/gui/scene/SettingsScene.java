package pro.gravit.launcher.client.gui.scene;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import oshi.SystemInfo;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class SettingsScene extends AbstractScene {
    private Pane componentList;

    public SettingsScene(JavaFXApplication application) {
        super("scenes/settings/settings.fxml", application);
    }

    @Override
    protected void doInit() {
        Node layout = LookupHelper.lookup(scene.getRoot(), "#settingsPane");
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#settingslist").getContent();
        sceneBaseInit(layout);
        LookupHelper.<ButtonBase>lookup(layout, "#apply").setOnAction((e) -> contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run());
        LookupHelper.<ButtonBase>lookup(layout, "#console").setOnAction((e) -> contextHelper.runCallback(() -> {
            if (application.gui.consoleStage == null)
                application.gui.consoleStage = new ConsoleStage(application);
            if (application.gui.consoleStage.isNullScene())
                application.gui.consoleStage.setScene(application.gui.consoleScene);
            application.gui.consoleStage.show();
        }).run());

        {
            Button langButton = LookupHelper.lookup(layout, "#lang");
            ContextMenu langChoice = langButton.getContextMenu();
            RuntimeSettings.LAUNCHER_LOCALE[] locales = RuntimeSettings.LAUNCHER_LOCALE.values();
            MenuItem[] items = new MenuItem[locales.length];
            for (int i = 0; i < locales.length; ++i) {
                items[i] = new MenuItem(locales[i].displayName);
                final int finalI = i;
                items[i].setOnAction((e) -> {
                    application.runtimeSettings.locale = locales[finalI];
                    application.messageManager.createNotification(application.getTranslation("runtime.scenes.settings.langChanged.head"), application.getTranslation("runtime.scenes.settings.langChanged.description"));
                });
            }
            langChoice.getItems().addAll(items);
            langButton.setOnMousePressed((e) -> {
                if (!e.isPrimaryButtonDown())
                    return;
                langChoice.show(langButton, e.getScreenX(), e.getScreenY());
            });
        }

        Slider ramSlider = LookupHelper.lookup(layout, "#ramSlider");
        Label ramLabel = LookupHelper.lookup(layout, "#serverImage", "#ramLabel");
        ramLabel.setText(Integer.toString(application.runtimeSettings.ram));
        try {
            SystemInfo systemInfo = new SystemInfo();
            ramSlider.setMax(systemInfo.getHardware().getMemory().getTotal() >> 20);
        } catch (Throwable e) {
            ramSlider.setMax(2048);
        }

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(3);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setValue(application.runtimeSettings.ram);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            application.runtimeSettings.ram = newValue.intValue();
            ramLabel.setText(application.runtimeSettings.ram <= 0 ? "Auto" : application.runtimeSettings.ram + " MiB");
        });
        Hyperlink updateDirLink = LookupHelper.lookup(layout, "#dirLabel", "#patch");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
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
                LogHelper.error(ex);
            }
            application.runtimeSettings.updatesDirPath = newDir.toString();
            application.runtimeSettings.updatesDir = newDir;
            updateDirLink.setText(application.runtimeSettings.updatesDirPath);
        });
        LookupHelper.<ButtonBase>lookup(layout, "#deleteDir").setOnAction((e) ->
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
                        }, true));
        add("Debug", application.runtimeSettings.debug, (value) -> application.runtimeSettings.debug = value);
        add("AutoEnter", application.runtimeSettings.autoEnter, (value) -> application.runtimeSettings.autoEnter = value);
        add("Fullscreen", application.runtimeSettings.fullScreen, (value) -> application.runtimeSettings.fullScreen = value);
    }

    public void add(String languageName, boolean value, Consumer<Boolean> onChanged) {
        String nameKey = String.format("runtime.scenes.settings.properties.%s.name", languageName.toLowerCase());
        String descriptionKey = String.format("runtime.scenes.settings.properties.%s.key", languageName.toLowerCase());
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
        container.getStyleClass().add("optContainer");
        checkBox.getStyleClass().add("optCheckbox");
        desc.getStyleClass().add("optDescription");
        FlowPane.setMargin(desc, new Insets(0, 0, 0, 30));
    }
}
