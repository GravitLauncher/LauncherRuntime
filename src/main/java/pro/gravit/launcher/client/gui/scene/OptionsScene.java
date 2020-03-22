package pro.gravit.launcher.client.gui.scene;

import com.google.gson.reflect.TypeToken;
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
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
import pro.gravit.launcher.profiles.optional.OptionalType;
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
    public Node layout;
    public Pane componentList;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        layout = LookupHelper.lookup(scene.getRoot(), "#optionsPane");
        sceneBaseInit(layout);
        ((ButtonBase) layout.lookup("#apply")).setOnAction((e) -> contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run());
        componentList = (Pane) ((ScrollPane) layout.lookup("#optionslist")).getContent();
    }

    public void reset() {
        componentList.getChildren().clear();
    }

    public void addProfileOptionals(ClientProfile profile) {
        for (OptionalFile e : profile.getOptional()) {
            add(e.name, e.info, e.mark, e.subTreeLevel, (val) -> {
                if (val)
                    profile.markOptional(e);
                else
                    profile.unmarkOptional(e);
            });
        }
    }

    public void add(String name, String description, boolean value, int padding, Consumer<Boolean> onChanged) {
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
    }
    public static class OptionalListEntryPair
    {
        public OptionalType type;
        public String name;

        public OptionalListEntryPair(OptionalFile f) {
            type = f.type;
            name = f.name;
        }
    }
    public static class OptionalListEntry
    {
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
        public void apply(ClientProfile profile)
        {
            for(OptionalListEntryPair s : enabled)
            {
                profile.markOptional(s.name, s.type);
            }
        }
    }
    public void saveAll() throws IOException
    {
        List<ClientProfile> profiles = application.runtimeStateMachine.getProfiles();
        if(profiles == null) return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        List<OptionalListEntry> list = new ArrayList<>(5);
        for(ClientProfile p : profiles)
        {
            OptionalListEntry entry = new OptionalListEntry();
            entry.name = p.getTitle();
            entry.profileUUID = p.getUUID();
            for(OptionalFile f : p.getOptional())
            {
                if(f.mark)
                    entry.enabled.add(new OptionalListEntryPair(f));
            }
            list.add(entry);
        }
        try(Writer writer = IOHelper.newWriter(optionsFile))
        {
            Launcher.gsonManager.gson.toJson(list, writer);
        }
    }
    public void loadAll() throws IOException
    {
        List<ClientProfile> profiles = application.runtimeStateMachine.getProfiles();
        if(profiles == null) return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        if(!Files.exists(optionsFile)) return;
        Type collectionType = new TypeToken<List<OptionalListEntry>>() {}.getType();
        try(Reader reader = IOHelper.newReader(optionsFile))
        {
            List<OptionalListEntry> list = Launcher.gsonManager.gson.fromJson(reader, collectionType);
            for(OptionalListEntry e : list)
            {
                ClientProfile profile = null;
                for(ClientProfile p : profiles)
                {
                    if(e.profileUUID != null ? e.profileUUID.equals(p.getUUID()) : p.getTitle().equals(e.name))
                        profile = p;
                }
                if(profile == null)
                {
                    LogHelper.warning("Optional: profile %s(%s) not found", e.name, e.profileUUID);
                    continue;
                }
                //
                for(OptionalListEntryPair f : e.enabled)
                {
                    try {
                        profile.markOptional(f.name, f.type);
                    } catch (Exception exc)
                    {
                        LogHelper.warning("Optional: in profile %s markOptional mod %s failed", profile.getTitle(), f.name);
                    }
                }
            }
        }
    }
}
