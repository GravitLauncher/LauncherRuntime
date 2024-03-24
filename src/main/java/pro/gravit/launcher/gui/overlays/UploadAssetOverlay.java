package pro.gravit.launcher.gui.overlays;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.stage.FileChooser;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.core.LauncherNetworkAPI;
import pro.gravit.launcher.base.events.request.AssetUploadInfoRequestEvent;
import pro.gravit.launcher.base.profiles.Texture;
import pro.gravit.launcher.base.request.Request;
import pro.gravit.launcher.base.request.RequestException;
import pro.gravit.launcher.base.request.cabinet.GetAssetUploadUrl;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UploadAssetOverlay extends CenterOverlay {
    private static final HttpClient client = HttpClient.newBuilder()
                                                       .build();
    private Button uploadSkin;
    private Button uploadCape;
    private CheckBox useSlim;
    private AssetUploadInfoRequestEvent.SlimSupportConf slimSupportConf;
    public UploadAssetOverlay(JavaFXApplication application) {
        super("overlay/uploadasset/uploadasset.fxml", application);
    }

    @Override
    public String getName() {
        return "uploadasset";
    }

    @Override
    protected void doInit() {
        uploadSkin = LookupHelper.lookup(layout, "#uploadskin");
        uploadCape = LookupHelper.lookup(layout, "#uploadcape");
        useSlim = LookupHelper.lookup(layout, "#useslim");
        uploadSkin.setOnAction((a) -> uploadAsset("SKIN", switch (slimSupportConf) {
            case USER -> new AssetOptions(useSlim.isSelected());
            case UNSUPPORTED, SERVER -> null;
        }));
        uploadCape.setOnAction((a) -> uploadAsset("CAPE", null));
        LookupHelper.<Button>lookupIfPossible(layout, "#close").ifPresent((b) -> b.setOnAction((e) -> hide(0, null)));
    }

    public void onAssetUploadInfo(AssetUploadInfoRequestEvent event) {
        boolean uploadSkinAvailable = event.available.contains("SKIN");
        boolean uploadCapeAvailable = event.available.contains("CAPE");
        uploadSkin.setVisible(uploadSkinAvailable);
        uploadCape.setVisible(uploadCapeAvailable);
        this.slimSupportConf = event.slimSupportConf;
        if(uploadSkinAvailable) {
            switch (event.slimSupportConf) {
                case USER -> useSlim.setVisible(true);
                case UNSUPPORTED, SERVER -> useSlim.setVisible(false);
            }
        }
    }

    public void uploadAsset(String name, AssetOptions options) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = chooser.showOpenDialog(currentStage.getStage());
        if(file == null) {
            return;
        }
        application.gui.processingOverlay.processRequest(currentStage, application.getTranslation("runtime.overlay.processing.text.uploadasset"), new GetAssetUploadUrl(name), (e) -> {
            String accessToken = e.token == null ? Request.getAccessToken() : e.token.accessToken;
            String boundary = SecurityHelper.toHex(SecurityHelper.randomBytes(32));
            String jsonOptions = options != null ? Launcher.gsonManager.gson.toJson(options) : "{}";
            byte[] preFileData;
            try(ByteArrayOutputStream output = new ByteArrayOutputStream(256)) {
                output.write("--".getBytes(StandardCharsets.UTF_8));
                output.write(boundary.getBytes(StandardCharsets.UTF_8));
                output.write("\r\nContent-Disposition: form-data; name=\"options\"\r\nContent-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                output.write(jsonOptions.getBytes(StandardCharsets.UTF_8));
                output.write("\r\n--".getBytes(StandardCharsets.UTF_8));
                output.write(boundary.getBytes(StandardCharsets.UTF_8));
                output.write("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"file\"\r\nContent-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                preFileData = output.toByteArray();
            } catch (IOException ex) {
                errorHandle(ex);
                return;
            }
            byte[] postFileData;
            try(ByteArrayOutputStream output = new ByteArrayOutputStream(128)) {
                output.write("\r\n--".getBytes(StandardCharsets.UTF_8));
                output.write(boundary.getBytes(StandardCharsets.UTF_8));
                output.write("--\r\n".getBytes(StandardCharsets.UTF_8));
                postFileData = output.toByteArray();
            } catch (IOException ex) {
                errorHandle(ex);
                return;
            }
            LogHelper.dev("%s<DATA>%s", new String(preFileData), new String(postFileData));
            try {
                client.sendAsync(HttpRequest.newBuilder()
                                         .uri(URI.create(e.url))
                                            .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(preFileData),
                                                                                    HttpRequest.BodyPublishers.ofFile(file.toPath()),
                                                                                    HttpRequest.BodyPublishers.ofByteArray(postFileData)))
                                            .header("Authorization", "Bearer "+accessToken)
                                            .header("Content-Type", "multipart/form-data; boundary=\""+boundary+"\"")
                                            .header("Accept", "application/json")
                                            .build(), HttpResponse.BodyHandlers.ofByteArray()).thenAccept((response) -> {
                                                LogHelper.dev(new String(response.body()));
                                                if(response.statusCode() >= 200 && response.statusCode() < 300) {
                                                    try(Reader reader = new InputStreamReader(new ByteArrayInputStream(response.body()))) {
                                                        Texture texture = Launcher.gsonManager.gson.fromJson(reader, UserTexture.class).toLauncherTexture();
                                                        URI skinUrl = new URI(texture.url);
                                                        if("SKIN".equals(name)) {
                                                            application.skinManager.addOrReplaceSkin(application.authService.getUsername(), skinUrl);
                                                            for(var scene : application.gui.getComponents()) {
                                                                if(scene.isInit() && scene instanceof SceneSupportUserBlock supportUserBlock) {
                                                                    supportUserBlock.getUserBlock().resetAvatar();
                                                                }
                                                            }
                                                        }
                                                        contextHelper.runInFxThread(() -> application.messageManager.createNotification(application.getTranslation("runtime.overlay.uploadasset.success.header"), application.getTranslation("runtime.overlay.uploadasset.success.description")));
                                                    } catch (IOException | URISyntaxException ex) {
                                                        errorHandle(ex);
                                                    }
                                                } else {
                                                    try(Reader reader = new InputStreamReader(new ByteArrayInputStream(response.body()))) {
                                                        UploadError error = Launcher.gsonManager.gson.fromJson(reader, UploadError.class);
                                                        errorHandle(new RequestException(error.error));
                                                    } catch (Exception ex) {
                                                        errorHandle(ex);
                                                    }
                                                }
                }).exceptionally((th) -> {
                    errorHandle(th);
                    return null;
                });
            } catch (Throwable ex) {
                errorHandle(ex);
            }
        }, this::errorHandle, (e) -> {});
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
