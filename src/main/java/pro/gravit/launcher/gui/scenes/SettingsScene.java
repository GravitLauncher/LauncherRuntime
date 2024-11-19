package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FXApplication;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.runtime.utils.SystemMemory;

import java.text.MessageFormat;
import java.util.function.Consumer;

@ResourcePath("scenes/settings/settings.fxml")
public class SettingsScene extends FxScene {
    private ServerButton serverButtonObj;
    private Slider ramSlider;
    private Label ramLabel;
    private ProfileFeatureAPI.ClientProfile profile;
    private LauncherBackendAPI.ClientProfileSettings settings;
    private Pane settingsContent;
    private Pane settingsList;
    private ComboBox<LauncherBackendAPI.Java> javaComboBox;
    @Override
    protected void doInit() {
        settingsContent = (Pane) this.<ScrollPane>lookup("#settingslist").getContent();
        settingsList = lookup(settingsContent, "#settings-list");
        javaComboBox = lookup(settingsContent, "#javaCombo");
        serverButtonObj = new ServerButton();
        inject(lookup("#serverButton"), serverButtonObj);
        ramSlider = lookup(settingsContent, "#ramSlider");
        ramLabel = lookup(settingsContent, "#ramLabel");
        ramSlider.valueProperty().addListener((obj, oldValue, value) -> {
            if(settings != null) {
                settings.setReservedMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL, value.intValue()*1024L*1024L);
                updateRamLabel();
            }
        });
        this.<Button>lookupIfPossible("#back").ifPresent(btn -> btn.setOnAction(e -> {
            this.stage.back();
        }));
    }

    public void save() {
        LauncherBackendAPIHolder.getApi().saveClientProfileSettings(settings);
    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        this.profile = profile;
        this.settings = settings;
        serverButtonObj.onProfile(profile);
        serverButtonObj.setOnSave(() -> {
            save();
            stage.back();
        });
        // RAM
        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(1);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setLabelFormatter(new RamSliderStringConverter());
        ramSlider.setMax(settings.getMaxMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL) >> 20);
        ramSlider.setValue(settings.getReservedMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL) >> 20);
        // Options
        settingsList.getChildren().clear();
        for(var e : this.settings.getAvailableFlags()) {
            add(e.name(), settings.hasFlag(e), (value) -> {
                if(value) {
                    settings.addFlag(e);
                } else {
                    settings.removeFlag(e);
                }
            }, false);
        }
        // Java
        javaComboBox.getItems().clear();
        javaComboBox.setConverter(new JavaVersionConverter(settings));
        javaComboBox.setCellFactory(new JavaVersionCellFactory(javaComboBox.getConverter()));
        LauncherBackendAPIHolder.getApi().getAvailableJava().thenAcceptAsync((list) -> {
            for(var java : list) {
                if(!settings.isCompatible(java)) {
                    continue;
                }
                javaComboBox.getItems().add(java);
            }
            javaComboBox.setValue(settings.getSelectedJava());
            if(javaComboBox.getValue() == null) {
                javaComboBox.setValue(settings.getRecommendedJava());
            }
        }, FxThreadExecutor.getInstance());
        javaComboBox.setOnAction((e) -> {
            LauncherBackendAPI.Java java = javaComboBox.getValue();
            if(java == null) return;
            settings.setSelectedJava(java);
        });

    }

    public void updateRamLabel() {
        ramLabel.setText(settings.getReservedMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL) == 0
                                 ? FXApplication.getTranslation("runtime.scenes.settings.ramAuto")
                                 : MessageFormat.format(FXApplication.getTranslation("runtime.scenes.settings.ram"),
                                                        settings.getReservedMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL) >> 20));
    }

    private static class RamSliderStringConverter extends StringConverter<Double> {
        @Override
        public String toString(Double object) {
            return "%.0fG".formatted(object / 1024);
        }

        @Override
        public Double fromString(String string) {
            return null;
        }
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
        add(FXApplication.getTranslation(nameKey, languageName), FXApplication.getTranslation(descriptionKey, languageName),
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

    private static class JavaVersionConverter extends StringConverter<LauncherBackendAPI.Java> {
        private LauncherBackendAPI.ClientProfileSettings settings;

        public JavaVersionConverter(LauncherBackendAPI.ClientProfileSettings settings) {
            this.settings = settings;
        }

        @Override
        public String toString(LauncherBackendAPI.Java object) {
            if (object == null) return "Unknown";
            String postfix = "";
            if (settings.isRecommended(object)) {
                postfix = "[RECOMMENDED]";
            }
            return "Java %d %s".formatted(object.getMajorVersion(), postfix);
        }

        @Override
        public LauncherBackendAPI.Java fromString(String string) {
            return null;
        }
    }

    private static class JavaVersionCellFactory implements Callback<ListView<LauncherBackendAPI.Java>, ListCell<LauncherBackendAPI.Java>> {

        private final StringConverter<LauncherBackendAPI.Java> converter;

        public JavaVersionCellFactory(StringConverter<LauncherBackendAPI.Java> converter) {
            this.converter = converter;
        }

        @Override
        public ListCell<LauncherBackendAPI.Java> call(ListView<LauncherBackendAPI.Java> param) {
            return new JavaVersionListCell(converter);
        }
    }

    private static class JavaVersionListCell extends ListCell<LauncherBackendAPI.Java> {
        private final StringConverter<LauncherBackendAPI.Java> converter;

        public JavaVersionListCell(StringConverter<LauncherBackendAPI.Java> converter) {
            this.converter = converter;
        }

        @Override
        protected void updateItem(LauncherBackendAPI.Java item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setTooltip(null);
            } else {
                setText(converter.toString(item));
                Tooltip tooltip = new Tooltip(item.getPath().toString());
                tooltip.setAnchorLocation(Tooltip.AnchorLocation.WINDOW_BOTTOM_LEFT);
                setTooltip(tooltip);
            }
        }
    }
}
