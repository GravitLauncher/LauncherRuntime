package pro.gravit.launcher.gui.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.core.backend.extensions.TextureUploadExtension;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.DesignConstants;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxComponent;
import pro.gravit.launcher.gui.core.utils.JavaFxUtils;

public class UserBlock extends FxComponent {

    private static final Logger logger =
            LoggerFactory.getLogger(UserBlock.class);

    private ImageView avatar;
    private Image originalAvatarImage;

    public UserBlock(Pane layout, JavaFXApplication application) {
        super(layout, application);
    }

    @Override
    public String getName() {
        return "userBlock";
    }

    @Override
    protected void doInit() {
        avatar = LookupHelper.lookup(layout, ImageView.class, "#avatar");
        avatar.setSmooth(false);
        originalAvatarImage = avatar.getImage();
        LookupHelper.lookupIfPossible(layout, ImageView.class, "#avatar").ifPresent((h) -> {
            try {
                JavaFxUtils.setStaticRadius(h, DesignConstants.AVATAR_IMAGE_RADIUS);
                h.setImage(originalAvatarImage);
            } catch (Throwable e) {
                logger.warn("Skin head error");
            }
        });
        var nodes = new pro.gravit.launcher.gui.core.ui.ViewNodes(layout, "components/userBlock.fxml");
        nodes.optional(Label.class, "#nickname").ifPresent(label -> {
            label.textProperty().bind(application.authService.username);
            scope.add(() -> label.textProperty().unbind());
        });
        scope.subscribe(application.skinManager.avatarProperty().subscribe(image -> {
            avatar.setImage(originalAvatarImage);
            if (image != null) resetAvatar();
        }));
        reset();
    }

    public void reset() {

        LookupHelper.lookupIfPossible(layout, Label.class, "#role")
                    .ifPresent((e) -> e.setText(application.authService.getMainRole()));
        avatar.setImage(originalAvatarImage);
        resetAvatar();
        TextureUploadExtension extension = LauncherBackendAPIHolder.getApi().getExtension(TextureUploadExtension.class);
        if(extension != null) {
            LookupHelper.lookupIfPossible(layout, Button.class, "#customization").ifPresent((h) -> {
                h.setVisible(true);
                h.setOnAction((a) -> application.gui.processingOverlay.processRequest(currentStage,
                        application.getTranslation("runtime.overlay.processing.text.uploadassetinfo"),
                        extension.fetchTextureUploadInfo(), (info) ->
                                contextHelper.runInFxThread(() ->
                                                                    application.gui.uploadAssetOverlay.show(currentStage, (f) -> application.gui.uploadAssetOverlay.onAssetUploadInfo(info))), this::errorHandle, (e) -> {}));
            });
        }
    }

    public void resetAvatar() {
        if (avatar == null) {
            return;
        }
        JavaFxUtils.putAvatarToImageView(application, application.authService.getUsername(), avatar);
    }
}
