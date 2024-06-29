package pro.gravit.launcher.gui.components;

import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.launcher.gui.basic.ResourcePath;

@ResourcePath("components/userBlock.fxml")
public class UserBlock extends Layer {
    private Label name;
    private Label role;
    private ImageView avatar;
    @Override
    protected void doInit() {
        name = lookup("#name");
        role = lookup("#role");
        avatar = lookup("#avatar");
        var user = LauncherBackendAPIHolder.getApi().getSelfUser();
        onUser(user);
    }

    public void onUser(SelfUser user) {
        if(user == null) {
            return;
        }
        name.setText(user.getUsername());
    }
}
