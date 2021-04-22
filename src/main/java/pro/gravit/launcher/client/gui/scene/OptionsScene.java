package pro.gravit.launcher.client.gui.scene;

import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
import pro.gravit.launcher.profiles.optional.OptionalType;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class OptionsScene extends AbstractScene {
    private Pane componentList;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        LookupHelper.<ButtonBase>lookup(layout, "#apply").setOnAction((e) -> {
            try {
                switchScene(application.gui.serverMenuScene);
            } catch (Exception exception) {
                LogHelper.error(exception);
            }
        });
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#optionslist").getContent();
    }

    @Override
    public void reset() {
        componentList.getChildren().clear();
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    @Override
    public String getName() {
        return "options";
    }

    public void addProfileOptionals(OptionalView view) {
        for (OptionalFile optionalFile : view.all) {
            optionalFile.clearAllWatchers();
            if (!optionalFile.visible)
                continue;

            Consumer<Boolean> setCheckBox = add(optionalFile.name, optionalFile.info, view.enabled.contains(optionalFile),
                    optionalFile.subTreeLevel, (isSelected) -> {
                        if (isSelected)
                            view.enable(optionalFile);
                        else
                            view.disable(optionalFile);
                    });
            optionalFile.registerWatcher((o, isSelected) -> setCheckBox.accept(isSelected));

        }
    }

    public Consumer<Boolean> add(String name, String description, boolean value, int padding, Consumer<Boolean> onChanged) {
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
        VBox.setMargin(container, new Insets(0, 0, 0, 50 * padding));
        return checkBox::setSelected;
    }

    public void saveAll() throws IOException {
        List<ClientProfile> profiles = application.runtimeStateMachine.getProfiles();
        Map<ClientProfile, OptionalView> optionalViewMap = application.runtimeStateMachine.getOptionalViewMap();
        if (profiles == null)
            return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        List<OptionalListEntry> list = new ArrayList<>(5);
        for (ClientProfile clientProfile : profiles) {
            OptionalListEntry entry = new OptionalListEntry();
            entry.name = clientProfile.getTitle();
            entry.profileUUID = clientProfile.getUUID();
            OptionalView view = optionalViewMap.get(clientProfile);
            view.all.forEach((optionalFile -> {
                if(optionalFile.visible) {
                    boolean isEnabled = view.enabled.contains(optionalFile);
                    entry.enabled.add(new OptionalListEntryPair(optionalFile, isEnabled));
                }
            }));
            list.add(entry);
        }
        try (Writer writer = IOHelper.newWriter(optionsFile)) {
            Launcher.gsonManager.gson.toJson(list, writer);
        }
    }

    public void loadAll() throws IOException {
        List<ClientProfile> profiles = application.runtimeStateMachine.getProfiles();
        Map<ClientProfile, OptionalView> optionalViewMap = application.runtimeStateMachine.getOptionalViewMap();
        if (profiles == null)
            return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        if (!Files.exists(optionsFile))
            return;

        Type collectionType = new TypeToken<List<OptionalListEntry>>() {
        }.getType();

        try (Reader reader = IOHelper.newReader(optionsFile)) {
            List<OptionalListEntry> list = Launcher.gsonManager.gson.fromJson(reader, collectionType);
            for (OptionalListEntry entry : list) {
                ClientProfile selectedProfile = null;
                for (ClientProfile clientProfile : profiles) {
                    if (entry.profileUUID != null ? entry.profileUUID.equals(clientProfile.getUUID()) : clientProfile.getTitle().equals(entry.name))
                        selectedProfile = clientProfile;
                }
                if (selectedProfile == null) {
                    LogHelper.warning("Optional: profile %s(%s) not found", entry.name, entry.profileUUID);
                    continue;
                }
                OptionalView view = optionalViewMap.get(selectedProfile);
                for (OptionalListEntryPair entryPair : entry.enabled) {
                    try {
                        OptionalFile file = selectedProfile.getOptionalFile(entryPair.name);
                        if (file.visible) {
                            if (entryPair.mark)
                                view.enable(file);
                            else
                                view.disable(file);
                        }
                    } catch (Exception exc) {
                        LogHelper.warning("Optional: in profile %s markOptional mod %s failed", selectedProfile.getTitle(), entryPair.name);
                    }
                }
            }
        }
    }

    public static class OptionalListEntryPair {
        public String name;
        public boolean mark;

        public OptionalListEntryPair(OptionalFile optionalFile, boolean enabled) {
            name = optionalFile.name;
            mark = enabled;
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
            return Objects.equals(profileUUID, that.profileUUID) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, profileUUID);
        }
    }
}
