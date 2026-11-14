package pro.gravit.launcher.gui.scenes.options;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OptionsTab {
    private final TabPane tabPane;
    private final JavaFXApplication application;
    private final Map<String, Tab> tabs = new HashMap<>();
    private final Map<ProfileFeatureAPI.OptionalMod, Consumer<Boolean>> watchers = new HashMap<>();

    public OptionsTab(JavaFXApplication application, TabPane tabPane) {
        this.tabPane = tabPane;
        this.application = application;
    }

    void callWatcher(ProfileFeatureAPI.OptionalMod file, Boolean value) {
        for (Map.Entry<ProfileFeatureAPI.OptionalMod, Consumer<Boolean>> v : watchers.entrySet()) {
            if (v.getKey() == file) {
                v.getValue().accept(value);
                break;
            }
        }
    }

    public void addProfileOptionals(LauncherBackendAPI.ClientProfileSettings profileSettings) {
        watchers.clear();

        List<ProfileFeatureAPI.OptionalMod> sorted = profileSettings.getAllOptionals()
                                                                    .stream()
                                                                    .filter(ProfileFeatureAPI.OptionalMod::isVisible)
                                                                    .sorted(Comparator
                                                                                    .comparingInt(ProfileFeatureAPI.OptionalMod::getDepth)
                                                                                    .thenComparing(m -> m.getCategory() == null ? "" : m.getCategory())
                                                                                    .thenComparing(ProfileFeatureAPI.OptionalMod::getName))
                                                                    .toList();

        for (ProfileFeatureAPI.OptionalMod optionalFile : sorted) {
            List<String> libraries = optionalFile.getDependencies() == null ? List.of() :
                    optionalFile.getDependencies()
                                .stream()
                                .filter(ProfileFeatureAPI.OptionalMod::isVisible)
                                .map(ProfileFeatureAPI.OptionalMod::getName)
                                .toList();


            Consumer<Boolean> setCheckBox =
                    add(optionalFile.getCategory() == null ? "GLOBAL" : optionalFile.getCategory(),
                        optionalFile.getName(),
                        optionalFile.getDescription(),
                        profileSettings.getEnabledOptionals().contains(optionalFile),
                        optionalFile.getDepth(),
                        (isSelected) -> {
                            if (isSelected) profileSettings.enableOptional(optionalFile, this::callWatcher);
                            else profileSettings.disableOptional(optionalFile, this::callWatcher);
                        }, libraries);
            watchers.put(optionalFile, setCheckBox);
        }
    }

    public VBox addTab(String name, String displayName) {
        Tab tab = new Tab();
        tab.setText(displayName);
        VBox vbox = new VBox();
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(vbox);
        scrollPane.setFitToWidth(true);
        tab.setContent(scrollPane);
        tabs.put(name, tab);
        tabPane.getTabs().add(tab);
        return vbox;
    }

    public Consumer<Boolean> add(String tab, String name, String description, boolean value, int padding,
            Consumer<Boolean> onChanged, List<String> libraries) {
        VBox vBox = new VBox();
        CheckBox checkBox = new CheckBox();
        Label label = new Label();
        vBox.getChildren().add(checkBox);
        vBox.getChildren().add(label);
        VBox.setMargin(vBox, new Insets(0, 0, 0, 15 * padding));
        vBox.getStyleClass().add("optional-container");
        checkBox.setSelected(value);
        checkBox.setText(name);
        checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        checkBox.getStyleClass().add("optional-checkbox");
        label.setText(description);
        label.setWrapText(true);
        label.getStyleClass().add("optional-label");
        if (!libraries.isEmpty()) {
            HBox hBox = new HBox();
            hBox.getStyleClass().add("optional-library-container");
            for (var l : libraries) {
                Label lib = new Label();
                lib.setText(l);
                lib.getStyleClass().add("optional-library");
                hBox.getChildren().add(lib);
            }
            vBox.getChildren().add(hBox);
        }
        VBox components;
        boolean needSelect = tabs.isEmpty();
        if (tabs.containsKey(tab)) {
            components = (VBox) ((ScrollPane) tabs.get(tab).getContent()).getContent();
        } else {
            components = addTab(tab, application
                                                 .getTranslation(String.format("runtime.scenes.options.tabs.%s", tab),
                                                                 tab));
        }
        components.getChildren().add(vBox);
        if (needSelect) {
            tabPane.getSelectionModel().select(0);
        }
        return checkBox::setSelected;
    }

    public void clear() {
        watchers.clear();
        tabPane.getTabs().clear();
        tabs.clear();
    }
}
