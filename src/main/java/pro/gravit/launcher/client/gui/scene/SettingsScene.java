package pro.gravit.launcher.client.gui.scene;

import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.stage.Stage;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.FunctionalBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

public class SettingsScene extends AbstractScene {
    public Node layout;
    public SettingsScene(Stage stage, JavaFXApplication application) {
        super("scenes/settings/settings.fxml", stage, application);
    }

    @Override
    protected void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(),  "#settingsPane");
        sceneBaseInit(layout);
        ((ButtonBase)layout.lookup("#apply")).setOnAction((e) -> {
            contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run();
        });
        Slider ramSlider = (Slider) layout.lookup("#ramSlider");
        Label ramLabel = (Label) layout.lookup("#settingsBackground").lookup("#ramLabel");
        ramLabel.setText(Integer.toString(application.settings.ram));
        ramSlider.setMax(FunctionalBridge.getJVMTotalMemory());
        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(3);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setValue(application.settings.ram);
        ramSlider.valueProperty().addListener((o, ov, nv) -> {
            application.settings.ram = nv.intValue();
            ramLabel.setText(application.settings.ram <= 0 ? "Auto" : application.settings.ram + " MiB");
        });
        Hyperlink updateDirLink = LookupHelper.lookup(layout, "#dirLabel", "#patch");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
    }
}
