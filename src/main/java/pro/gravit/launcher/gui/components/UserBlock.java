package pro.gravit.launcher.gui.components;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.base.events.request.GetAssetUploadUrlRequestEvent;
import pro.gravit.launcher.base.request.cabinet.AssetUploadInfoRequest;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.DesignConstants;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.utils.JavaFxUtils;
import pro.gravit.utils.helper.LogHelper;

public class UserBlock {
    private final JavaFXApplication application;
    private final Pane layout;
    private final AbstractScene.SceneAccessor sceneAccessor;
    private final ImageView avatar;
    private final Image originalAvatarImage;

    public UserBlock(Pane layout, AbstractScene.SceneAccessor sceneAccessor) {
        this.application = sceneAccessor.getApplication();
        this.layout = layout;
        this.sceneAccessor = sceneAccessor;
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent((h) -> {
            try {
                JavaFxUtils.setStaticRadius(h, DesignConstants.AVATAR_IMAGE_RADIUS);
                h.setImage(originalAvatarImage);
            } catch (Throwable e) {
                LogHelper.warning("Skin head error");
            }
        });
        reset();
    }

    public void reset() {
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname")
                    .ifPresent((e) -> e.setText(application.authService.getUsername()));
        LookupHelper.<Label>lookupIfPossible(layout, "#role")
                    .ifPresent((e) -> e.setText(application.authService.getMainRole()));
        avatar.setImage(originalAvatarImage);
        resetAvatar();
        if(application.authService.isFeatureAvailable(GetAssetUploadUrlRequestEvent.FEATURE_NAME)) {
            LookupHelper.<Button>lookupIfPossible(layout, "#customization").ifPresent((h) -> {
                h.setVisible(true);
                h.setOnAction((a) -> sceneAccessor.processRequest(application.getTranslation("runtime.overlay.processing.text.uploadassetinfo"), new AssetUploadInfoRequest(), (info) -> sceneAccessor.runInFxThread(() -> sceneAccessor.showOverlay(application.gui.uploadAssetOverlay, (f) -> application.gui.uploadAssetOverlay.onAssetUploadInfo(info))), sceneAccessor::errorHandle, (e) -> {}));
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
