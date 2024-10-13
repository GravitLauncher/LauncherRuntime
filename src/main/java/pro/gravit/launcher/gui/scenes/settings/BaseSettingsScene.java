package pro.gravit.launcher.gui.scenes.settings;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;

import java.util.function.Consumer;

public abstract class BaseSettingsScene extends AbstractScene {
    protected Pane componentList;
    protected Pane settingsList;

    public BaseSettingsScene(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
    }

    @Override
    protected void doInit() {
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#settingslist").getContent();
        settingsList = LookupHelper.lookup(componentList, "#settings-list");
    }

    @Override
    public void reset() {
        settingsList.getChildren().clear();
        Label settingsListHeader = new Label(application.getTranslation("runtime.scenes.settings.header.options"));
        settingsListHeader.getStyleClass().add("settings-header");
        settingsList.getChildren().add(settingsListHeader);
    }

    public void add(String languageName, boolean value, Consumer<Boolean> onChanged, boolean disabled) {
        String nameKey = "runtime.scenes.settings.properties.%s.name".formatted(languageName.toLowerCase());
        String descriptionKey;
        if(disabled) {
            descriptionKey = "runtime.scenes.settings.properties.%s.disabled".formatted(
                    languageName.toLowerCase());
        } else {
            descriptionKey = "runtime.scenes.settings.properties.%s.description".formatted(
                    languageName.toLowerCase());
        }
        add(application.getTranslation(nameKey, languageName), application.getTranslation(descriptionKey, languageName),
            value, onChanged, disabled);
    }

    public void add(String name, String description, boolean value, Consumer<Boolean> onChanged, boolean disabled) {
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
        if (!disabled) {
            checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        } else {
            checkBox.setDisable(true);
        }
        header.setText(name);
        label.setText(description);
        label.setWrapText(true);
        vBox.getChildren().add(header);
        vBox.getChildren().add(label);
        hBox.getChildren().add(checkBox);
        hBox.getChildren().add(vBox);
        settingsList.getChildren().add(hBox);
    }
}
