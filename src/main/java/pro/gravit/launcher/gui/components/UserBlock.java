package pro.gravit.launcher.gui.components;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.launcher.gui.basic.ResourcePath;

import java.io.ByteArrayInputStream;

public class UserBlock extends Layer {
    private Label name;
    private Label role;
    private ImageView avatar;

    public UserBlock(Pane root) {
        super(root);
    }

    @Override
    protected void doInit() {
        name = lookup("#nickname");
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
        var assets = user.getAssets();
        if(assets.containsKey("AVATAR")) {
            LauncherBackendAPIHolder.getApi().fetchTexture(assets.get("AVATAR")).thenAcceptAsync((texture) -> {
                avatar.setImage(new Image(new ByteArrayInputStream(texture)));
            }, FxThreadExecutor.getInstance());
        } else if(assets.containsKey("SKIN")) {
            LauncherBackendAPIHolder.getApi().fetchTexture(assets.get("SKIN")).thenAcceptAsync((texture) -> {
                avatar.setImage(new Image(new ByteArrayInputStream(texture)));
            }, FxThreadExecutor.getInstance());
        }
    }
}
