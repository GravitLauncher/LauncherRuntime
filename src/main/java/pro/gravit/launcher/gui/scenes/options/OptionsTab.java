package pro.gravit.launcher.gui.scenes.options;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.base.profiles.optional.OptionalFile;
import pro.gravit.launcher.base.profiles.optional.OptionalView;
import pro.gravit.launcher.gui.JavaFXApplication;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OptionsTab {
    private final TabPane tabPane;
    private final JavaFXApplication application;
    private final Map<String, Tab> tabs = new HashMap<>();
    private OptionalView optionalView;
    private final Map<OptionalFile, Consumer<Boolean>> watchers = new HashMap<>();

    public OptionsTab(JavaFXApplication application, TabPane tabPane) {
        this.tabPane = tabPane;
        this.application = application;
    }

    void callWatcher(OptionalFile file, Boolean value) {
        for (Map.Entry<OptionalFile, Consumer<Boolean>> v : watchers.entrySet()) {
            if (v.getKey() == file) {
                v.getValue().accept(value);
                break;
            }
        }
    }

    public void addProfileOptionals(OptionalView view) {
        this.optionalView = new OptionalView(view);
        watchers.clear();
        for (OptionalFile optionalFile : optionalView.all) {
            if (!optionalFile.visible) continue;
            List<String> libraries = optionalFile.dependencies == null ? List.of() : Arrays.stream(
                    optionalFile.dependencies).map(OptionalFile::getName).toList();

            Consumer<Boolean> setCheckBox =
                    add(optionalFile.category == null ? "GLOBAL" : optionalFile.category, optionalFile.name,
                        optionalFile.info, optionalView.enabled.contains(optionalFile),
                        optionalFile.subTreeLevel,
                        (isSelected) -> {
                            if (isSelected) optionalView.enable(optionalFile, true, this::callWatcher);
                            else optionalView.disable(optionalFile, this::callWatcher);
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
        VBox.setMargin(vBox, new Insets(0, 0, 0, 30 * --padding));
        vBox.setOnMouseClicked((e) -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                checkBox.setSelected(!checkBox.isSelected());
                onChanged.accept(checkBox.isSelected());
            }
        });
        vBox.setOnTouchPressed((e) -> {
            checkBox.setSelected(!checkBox.isSelected());
            onChanged.accept(checkBox.isSelected());
        });
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
        tabPane.getTabs().clear();
        tabs.clear();
    }

    public OptionalView getOptionalView() {
        return optionalView;
    }
}