package pro.gravit.launcher.client.gui.scene;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;

import java.util.function.Consumer;

public class OptionsScene extends AbstractScene {
    public Node layout;
    public Pane componentList;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(), "#optionsPane");
        sceneBaseInit(layout);
        ((ButtonBase) layout.lookup("#apply")).setOnAction((e) -> {
            contextHelper.runCallback(() -> application.setMainScene(application.gui.serverMenuScene)).run();
        });
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
        checkBox.setOnAction((e) -> {
            onChanged.accept(checkBox.isSelected());
        });
        componentList.getChildren().add(container);
        container.getStyleClass().add("optContainer");
        checkBox.getStyleClass().add("optCheckbox");
        desc.getStyleClass().add("optDescription");
        FlowPane.setMargin(desc, new Insets(0, 0, 0, 30));
        VBox.setMargin(container, new Insets(0, 0, 0, 50 * padding));
    }
}
