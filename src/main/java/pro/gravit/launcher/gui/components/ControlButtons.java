package pro.gravit.launcher.gui.components;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.utils.helper.LogHelper;

public class ControlButtons extends Layer {

    public ControlButtons(Pane root) {
        super(root);
    }

    @Override
    protected void doInit() {
        this.<Button>lookupIfPossible("#exit").ifPresent(btn -> btn.setOnAction(e -> {
            try {
                LauncherBackendAPIHolder.getApi().shutdown();
            } catch (Throwable ex) {
                LogHelper.error(ex);
            }
            Platform.exit();
        }));
        this.<Button>lookupIfPossible("#minimize").ifPresent(btn -> btn.setOnAction(e -> {
            this.stage.hide();
        }));
    }
}
