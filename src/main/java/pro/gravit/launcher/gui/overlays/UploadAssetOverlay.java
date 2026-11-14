package pro.gravit.launcher.gui.overlays;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.stage.FileChooser;
import pro.gravit.launcher.base.profiles.Texture;
import pro.gravit.launcher.core.LauncherNetworkAPI;
import pro.gravit.launcher.core.api.features.TextureUploadFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.core.backend.extensions.TextureUploadExtension;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class UploadAssetOverlay extends CenterOverlay {
    private static final HttpClient client = HttpClient.newBuilder()
                                                       .build();
    private Button uploadSkin;
    private Button uploadCape;
    private CheckBox useSlim;
    private final AtomicBoolean requireManualSlimSkinSelection = new AtomicBoolean(true);
    public UploadAssetOverlay(JavaFXApplication application) {
        super("overlay/uploadasset/uploadasset.fxml", application);
    }

    @Override
    public String getName() {
        return "uploadasset";
    }

    @Override
    protected void doInit() {
        uploadSkin = LookupHelper.lookup(layout, Button.class, "#uploadskin");
        uploadCape = LookupHelper.lookup(layout, Button.class, "#uploadcape");
        useSlim = LookupHelper.lookup(layout, CheckBox.class, "#useslim");
        uploadSkin.setOnAction((a) -> uploadAsset("SKIN", requireManualSlimSkinSelection.get() ?
                new AssetOptions(useSlim.isSelected()) : null));
        uploadCape.setOnAction((a) -> uploadAsset("CAPE", null));
        LookupHelper.lookupIfPossible(layout, Button.class, "#close").ifPresent((b) -> b.setOnAction((e) -> hide(0, null)));
    }

    public void onAssetUploadInfo(TextureUploadFeatureAPI.TextureUploadInfo event) {
        boolean uploadSkinAvailable = event.getAvailable().contains("SKIN");
        boolean uploadCapeAvailable = event.getAvailable().contains("CAPE");
        uploadSkin.setVisible(uploadSkinAvailable);
        uploadCape.setVisible(uploadCapeAvailable);
        if(uploadSkinAvailable) {
            requireManualSlimSkinSelection.set(event.isRequireManualSlimSkinSelect());
            useSlim.setVisible(event.isRequireManualSlimSkinSelect());
        }
    }

    public void uploadAsset(String name, AssetOptions options) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = chooser.showOpenDialog(currentStage.getStage());
        if(file == null) {
            return;
        }
        try {
            if (file.length() > 8 * 1024 * 1024) throw new IOException("Texture exceeds 8 MiB");
            TextureUploadExtension textureUploadExtension = LauncherBackendAPIHolder.getApi().getExtension(
                    TextureUploadExtension.class);
            if (textureUploadExtension == null) throw new IOException("Texture upload unavailable");
            uploadSkin.setDisable(true); uploadCape.setDisable(true);
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return IOHelper.read(file.toPath()); }
                catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
            }, application.workers).thenCompose(bytes -> textureUploadExtension.uploadTexture(name, bytes,
                    options == null ? null : new TextureUploadFeatureAPI.UploadSettings(options.modelSlim()))).thenAccept((texture) -> {
                contextHelper.runInFxThread(() -> {
                    if ("SKIN".equals(name)) application.skinManager.loadSkin(application.authService.getUsername(), texture);
                });
                contextHelper.runInFxThread(() -> {
                    application.messageManager.createNotification(application.getTranslation("runtime.overlay.uploadasset.success.header"), application.getTranslation("runtime.overlay.uploadasset.success.description"));
                    hide(0, null);
                });
            }).whenComplete((value, error) -> contextHelper.runInFxThread(() -> {
                uploadSkin.setDisable(false); uploadCape.setDisable(false);
                if (error != null) errorHandle(error);
            }));
        } catch (IOException e) {
            errorHandle(e);
        }
    }

    public static final class AssetOptions {
        @LauncherNetworkAPI
        private final boolean modelSlim;

        public AssetOptions(boolean modelSlim) {
            this.modelSlim = modelSlim;
        }

        public boolean modelSlim() {
            return modelSlim;
        }

    }

    public record UploadError(@LauncherNetworkAPI String error) {

    }

    public record UserTexture(@LauncherNetworkAPI String url,@LauncherNetworkAPI String digest,@LauncherNetworkAPI Map<String, String> metadata) {

        Texture toLauncherTexture() {
            return new Texture(url, SecurityHelper.fromHex(digest), metadata);
        }

        }

    @Override
    public void reset() {

    }
}
