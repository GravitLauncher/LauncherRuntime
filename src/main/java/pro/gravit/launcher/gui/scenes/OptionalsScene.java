package pro.gravit.launcher.gui.scenes;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FXApplication;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.components.UserBlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ResourcePath("scenes/options/options.fxml")
public class OptionalsScene extends FxScene {
    private final Map<String, Tab> tabs = new HashMap<>();
    private TabPane tabPane;
    private ServerButton serverButtonObj;
    private ProfileFeatureAPI.ClientProfile profile;
    private LauncherBackendAPI.ClientProfileSettings settings;
    private final Map<ProfileFeatureAPI.OptionalMod, Consumer<Boolean>> watchers = new HashMap<>();
    @Override
    protected void doInit() {
        serverButtonObj = new ServerButton();
        inject(lookup("#serverButton"), serverButtonObj);
        use(lookup("#userBlock"), UserBlock::new);
        tabPane = lookup("#tabPane");
        this.<Button>lookupIfPossible("#back").ifPresent(btn -> btn.setOnAction(e -> {
            this.stage.back();
        }));
    }

    public void onProfile(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        this.profile = profile;
        this.settings = settings;
        clear();
        serverButtonObj.onProfile(profile);
        serverButtonObj.setOnReset(() -> {
            onProfile(profile, LauncherBackendAPIHolder.getApi().makeClientProfileSettings(profile));
        });
        serverButtonObj.setOnSave(() -> {
            LauncherBackendAPIHolder.getApi().saveClientProfileSettings(settings);
            stage.back();
        });
        var enabled = settings.getEnabledOptionals();
        for(var optional : profile.getOptionalMods()) {
            if(!optional.isVisible()) {
                continue;
            }
            var watcher = add(optional.getCategory(), optional.getName(), optional.getDescription(), enabled.contains(optional), 0, (value) -> {
                if(value) {
                    settings.enableOptional(optional, this::onChanged);
                } else {
                    settings.disableOptional(optional, this::onChanged);
                }
            }, optional.getDependencies().stream().map(ProfileFeatureAPI.OptionalMod::getName).toList());
            watchers.put(optional, watcher);
        }
    }

    private void onChanged(ProfileFeatureAPI.OptionalMod optionalMod, boolean b) {
        var watcher = watchers.get(optionalMod);
        if(watcher != null) {
            watcher.accept(b);
        }
    }

    public void clear() {
        tabPane.getTabs().clear();
        tabs.clear();
        watchers.clear();
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
            components = addTab(tab, FXApplication
                    .getTranslation(String.format("runtime.scenes.options.tabs.%s", tab),
                                    tab));
        }
        components.getChildren().add(vBox);
        if (needSelect) {
            tabPane.getSelectionModel().select(0);
        }
        return checkBox::setSelected;
    }
}
