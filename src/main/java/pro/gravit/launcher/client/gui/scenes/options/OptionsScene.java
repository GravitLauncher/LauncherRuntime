package pro.gravit.launcher.client.gui.scenes.options;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerButton;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
import pro.gravit.launcher.profiles.optional.OptionalView;

import java.util.*;
import java.util.function.Consumer;

public class OptionsScene extends AbstractScene {
    private Pane componentList;
    private OptionalView optionalView;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#optionslist").getContent();
    }

    @Override
    public void reset() {
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.profilesService.getProfile();
        ServerButton serverButton = ServerMenuScene.getServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(null, (e) -> {
            try {
                application.profilesService.setOptionalView(profile, optionalView);
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        serverButton.enableResetButton(null, (e) -> {
            componentList.getChildren().clear();
            application.profilesService.setOptionalView(profile, new OptionalView(profile));
            addProfileOptionals(application.profilesService.getOptionalView());
        });
        componentList.getChildren().clear();
        LookupHelper.<Button>lookupIfPossible(header, "#back").ifPresent(x -> x.setOnAction((e) -> {
            try {
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
    }

    @Override
    public String getName() {
        return "options";
    }

    private final Map<OptionalFile, Consumer<Boolean>> watchers = new HashMap<>();

    private void callWatcher(OptionalFile file, Boolean value) {
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

            Consumer<Boolean> setCheckBox =
                    add(optionalFile.name, optionalFile.info, optionalView.enabled.contains(optionalFile),
                        optionalFile.subTreeLevel,
                        (isSelected) -> {
                            if (isSelected) optionalView.enable(optionalFile, true, this::callWatcher);
                            else optionalView.disable(optionalFile, this::callWatcher);
                        });
            watchers.put(optionalFile, setCheckBox);
        }
    }

    public Consumer<Boolean> add(String name, String description, boolean value, int padding,
            Consumer<Boolean> onChanged) {
        VBox vBox = new VBox();
        CheckBox checkBox = new CheckBox();
        Label label = new Label();
        vBox.getChildren().add(checkBox);
        vBox.getChildren().add(label);
        VBox.setMargin(vBox, new Insets(0, 0, 0, 30 * --padding));
        vBox.getStyleClass().add("optional-container");
        checkBox.setSelected(value);
        checkBox.setText(name);
        checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        checkBox.getStyleClass().add("optional-checkbox");
        label.setText(description);
        label.setWrapText(true);
        label.getStyleClass().add("optional-label");
        componentList.getChildren().add(vBox);
        return checkBox::setSelected;
    }

    public static class OptionalListEntryPair {
        public String name;
        public boolean mark;
        public OptionalView.OptionalFileInstallInfo installInfo;

        public OptionalListEntryPair(OptionalFile optionalFile, boolean enabled,
                OptionalView.OptionalFileInstallInfo installInfo) {
            name = optionalFile.name;
            mark = enabled;
            this.installInfo = installInfo;
        }
    }

    public static class OptionalListEntry {
        public List<OptionalListEntryPair> enabled = new LinkedList<>();
        public String name;
        public UUID profileUUID;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OptionalListEntry that = (OptionalListEntry) o;
            return Objects.equals(profileUUID, that.profileUUID) && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, profileUUID);
        }
    }
}
