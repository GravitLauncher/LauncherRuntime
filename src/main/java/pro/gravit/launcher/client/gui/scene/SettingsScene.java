package pro.gravit.launcher.client.gui.scene;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class SettingsScene extends AbstractScene {
    public Node layout;
    public Pane componentList;

    public SettingsScene(JavaFXApplication application) {
        super("scenes/settings/settings.fxml", application);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doInit() {
        layout = LookupHelper.lookup(scene.getRoot(), "#settingsPane");
        componentList = (Pane) ((ScrollPane) layout.lookup("#settingslist")).getContent();
        sceneBaseInit(layout);
        ((ButtonBase) layout.lookup("#apply")).setOnAction((e) -> contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run());
        ((ButtonBase) layout.lookup("#console")).setOnAction((e) -> contextHelper.runCallback(() -> {
            if (application.gui.consoleStage == null) application.gui.consoleStage = new ConsoleStage(application);
            if (application.gui.consoleStage.isNullScene())
                application.gui.consoleStage.setScene(application.gui.consoleScene);
            application.gui.consoleStage.show();
        }).run());
        {
            Button langButton = (Button) layout.lookup("#lang");
            ContextMenu langChoice = langButton.getContextMenu();
            RuntimeSettings.LAUNCHER_LOCALE[] locales = RuntimeSettings.LAUNCHER_LOCALE.values();
            MenuItem[] items = new MenuItem[locales.length];
            for(int i=0;i<locales.length;++i)
            {
                items[i] = new MenuItem(locales[i].displayName);
                int finalI = i;
                items[i].setOnAction((e) -> {
                    application.runtimeSettings.locale = locales[finalI];
                    application.messageManager.createNotification( application.getLangResource("runtime.scenes.settings.langChanged.head"), application.getLangResource("runtime.scenes.settings.langChanged.description"));
                });
            }
            langChoice.getItems().addAll(items);
            langButton.setOnMousePressed((e) -> {
                if(!e.isPrimaryButtonDown()) return;
                langChoice.show(langButton, e.getScreenX() ,e.getScreenY());
            });
        }
        Slider ramSlider = (Slider) layout.lookup("#ramSlider");
        Label ramLabel = (Label) layout.lookup("#serverImage").lookup("#ramLabel");
        ramLabel.setText(Integer.toString(application.runtimeSettings.ram));
        try {
            SystemInfo systemInfo = new SystemInfo();
            ramSlider.setMax(systemInfo.getHardware().getMemory().getTotal() >> 20);
        } catch (Throwable e)
        {
            ramSlider.setMax(2048);
        }

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(3);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setValue(application.runtimeSettings.ram);
        ramSlider.valueProperty().addListener((o, ov, nv) -> {
            application.runtimeSettings.ram = nv.intValue();
            ramLabel.setText(application.runtimeSettings.ram <= 0 ? "Auto" : application.runtimeSettings.ram + " MiB");
        });
        Hyperlink updateDirLink = LookupHelper.lookup(layout, "#dirLabel", "#patch");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
        ((ButtonBase) layout.lookup("#changeDir")).setOnAction((e) -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Сменить директорию загрузок");
            directoryChooser.setInitialDirectory(DirBridge.dir.toFile());
            File choose = directoryChooser.showDialog(application.getMainStage().stage);
            if(choose == null) return;
            Path newDir = choose.toPath().toAbsolutePath();
            DirBridge.dirUpdates = newDir;
            application.runtimeSettings.updatesDirPath = newDir.toString();
            application.runtimeSettings.updatesDir = newDir;
            updateDirLink.setText(application.runtimeSettings.updatesDirPath);
        });
        ((ButtonBase) layout.lookup("#deleteDir")).setOnAction((e) -> {
            application.messageManager.showApplyDialog(application.getLangString("runtime.scenes.settings.deletedir.header"),
                                                application.getLangResource("runtime.scenes.settings.deletedir.description"),
                    () -> {
                        LogHelper.debug("Delete dir: %s", DirBridge.dirUpdates);
                        try {
                            IOHelper.deleteDir(DirBridge.dirUpdates, false);
                        } catch (IOException ex) {
                            LogHelper.error(ex);
                            application.messageManager.createNotification(application.getLangResource("runtime.scenes.settings.deletedir.fail.header"),
                                    application.getLangResource("runtime.scenes.settings.deletedir.fail.description"));
                        }
                    }, () -> {}, true);
        });
        add("Debug", application.runtimeSettings.debug, (value) -> application.runtimeSettings.debug = value);
        add("AutoEnter", application.runtimeSettings.autoEnter, (value) -> application.runtimeSettings.autoEnter = value);
        add("Fullscreen", application.runtimeSettings.fullScreen, (value) -> application.runtimeSettings.fullScreen = value);
    }
    public void add(String langName, boolean value, Consumer<Boolean> onChanged) {
        String langBase = "runtime.scenes.settings.properties.".concat(langName.toLowerCase()).concat(".");
        add(application.getLangString(langBase.concat("name"), langName), application.getLangString(langBase.concat("description"), langName), value, onChanged);
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
