package pro.gravit.launcher.gui.overlays;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.gui.JavaFXApplication;

public abstract class CenterOverlay extends AbstractOverlay {
    private volatile Pane overrideFxmlRoot;
    public CenterOverlay(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
    }

    @Override
    protected synchronized Parent getFxmlRoot() {
        if(overrideFxmlRoot == null) {
            Parent fxmlRoot = super.getFxmlRoot();
            HBox hBox = new HBox();
            hBox.getChildren().add(fxmlRoot);
            hBox.setAlignment(Pos.CENTER);
            VBox vbox = new VBox();
            vbox.setAlignment(Pos.CENTER);
            vbox.getChildren().add(hBox);
            overrideFxmlRoot = vbox;
        }
        return overrideFxmlRoot;
    }
}
