package pro.gravit.launcher.client.gui.scene;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;

import java.util.function.Consumer;

public class SettingsScene extends AbstractScene {
    public Node layout;
    public Pane componentList;
    public SettingsScene(JavaFXApplication application) {
        super("scenes/settings/settings.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(),  "#settingsPane");
        componentList = (Pane) ((ScrollPane)layout.lookup("#settingslist")).getContent();
        sceneBaseInit(layout);
        ((ButtonBase)layout.lookup("#apply")).setOnAction((e) -> {
            contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run();
        });
        ((ButtonBase)layout.lookup("#console")).setOnAction((e) -> {
            contextHelper.runCallback(() -> {
                if(application.gui.consoleStage == null) application.gui.consoleStage = new ConsoleStage(application);
                if(application.gui.consoleStage.isNullScene()) application.gui.consoleStage.setScene(application.gui.consoleScene);
                application.gui.consoleStage.show();
            }).run();
        });
        Slider ramSlider = (Slider) layout.lookup("#ramSlider");
        Label ramLabel = (Label) layout.lookup("#settingsBackground").lookup("#ramLabel");
        ramLabel.setText(Integer.toString(application.runtimeSettings.ram));
        ramSlider.setMax(2048); //TODO
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
        add("Debug", "debug mode", application.runtimeSettings.debug, (value) -> application.runtimeSettings.debug=value);
    }

    public void add(String name, String description, boolean value, Consumer<Boolean> onChanged)
    {
        Pane container = new Pane();
        container.getStyleClass().add("settingsContainer");
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(value);
        checkBox.getStyleClass().add("settingsCheckbox");
        checkBox.setText(name);
        Text desc = new Text();
        desc.setText(description);
        desc.getStyleClass().add("settingsDescription");
        container.getChildren().add(checkBox);
        container.getChildren().add(desc);

        checkBox.setOnAction((e) -> {
            onChanged.accept(checkBox.isSelected());
        });
        componentList.getChildren().add(container);
    }
}
