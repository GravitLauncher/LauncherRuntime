package pro.gravit.launcher.client.gui.scene;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.ClientLauncherProcess;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.overlay.DebugOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.launcher.request.auth.SetProfileRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;
import sun.awt.image.IntegerComponentRaster;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ServerMenuScene extends AbstractScene {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private Node layout;
    private ImageView avatar;
    private Node lastSelectedServerButton;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(), "#layout", "#serverMenu");
        sceneBaseInit(layout);
        LookupHelper.<Labeled>lookup(layout, "#nickname").setText(application.runtimeStateMachine.getUsername());
        avatar = LookupHelper.lookup(layout, "#avatar");
        Map<ClientProfile, Future<Pane>> futures = new HashMap<>();
        Map<ClientProfile, Integer> positionMap = new HashMap<>();

        {
            int position = 0;
            for (ClientProfile profile : application.runtimeStateMachine.getProfiles()) {
                String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profile.getTitle());
                URL customFxml = application.tryResource(customFxmlName);
                if (customFxml != null) {
                    futures.put(profile, application.getNonCachedFxmlAsync(customFxmlName, IOHelper.newInput(customFxml)));
                } else {
                    futures.put(profile, application.getNonCachedFxmlAsync(SERVER_BUTTON_FXML));
                }
                positionMap.put(profile, position);
                position++;
            }
        }

        Pane serverList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#serverlist").getContent();
        futures.forEach((profile, future) -> {
            try {
                Pane pane = future.get();
                AtomicReference<ServerPinger.Result> pingerResult = new AtomicReference<>();
                LookupHelper.<Hyperlink>lookup(pane, "#nameServer").setText(profile.getTitle());
                LookupHelper.<Text>lookup(pane, "#genreServer").setText(profile.getVersion().toString());
                profile.updateOptionalGraph();
                EventHandler<? super MouseEvent> handle = (event) -> {
                    if (!event.getButton().equals(MouseButton.PRIMARY))
                        return;
                    if (lastSelectedServerButton != null)
                        lastSelectedServerButton.getStyleClass().remove("serverButtonsActive");
                    lastSelectedServerButton = pane;
                    lastSelectedServerButton.getStyleClass().add("serverButtonsActive");
                    changeServer(profile, pingerResult.get());
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                };
                pane.setOnMouseClicked(handle);
                LookupHelper.lookup(pane, "#nameServer").setOnMouseClicked(handle);
                pane.setOnMouseEntered((e) -> LookupHelper.lookup(pane, "#nameServer").getStyleClass().add("nameServerActive"));
                pane.setOnMouseExited((e) -> LookupHelper.lookup(pane, "#nameServer").getStyleClass().remove("nameServerActive"));
                ///////////
                int profilePosition = positionMap.get(profile);
                if (profilePosition >= serverList.getChildren().size())
                    profilePosition = serverList.getChildren().size();
                serverList.getChildren().add(profilePosition, pane);
                ///////////
                application.workers.submit(() -> {
                    ServerPinger pinger = new ServerPinger(profile);
                    ServerPinger.Result result;
                    try {
                        result = pinger.ping();
                    } catch (IOException e) {
                        result = new ServerPinger.Result(0, 0, "0 / 0");
                    }
                    pingerResult.set(result);
                    ServerPinger.Result finalResult = result;
                    contextHelper.runInFxThread(() -> {
                        LookupHelper.<Text>lookup(pane, "#online").setText(String.valueOf(finalResult.onlinePlayers));
                        if (application.runtimeStateMachine.getProfile() != null &&
                                application.runtimeStateMachine.getProfile() == profile) {
                            LookupHelper.<Text>lookup(layout, "#headingOnline").setText(String.format("%d / %d", finalResult.onlinePlayers, finalResult.maxPlayers));
                        }
                    });
                });
                if (profile.getUUID() != null && profile.getUUID().equals(application.runtimeSettings.lastProfile)) {
                    changeServer(profile, pingerResult.get());
                }
            } catch (InterruptedException | ExecutionException e) {
                LogHelper.error(e);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#clientSettings").setOnAction((e) -> {
            try {
                if (application.runtimeStateMachine.getProfile() == null)
                    return;
                application.setMainScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.runtimeStateMachine.getProfile());
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#settings").setOnAction((e) -> {
            try {
                application.setMainScene(application.gui.settingsScene);
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#exit").setOnAction((e) ->
                application.messageManager.showApplyDialog(application.getTranslation("runtime.scenes.settings.exitDialog.header"),
                        application.getTranslation("runtime.scenes.settings.exitDialog.description"), () ->
                                processRequest(application.getTranslation("runtime.scenes.settings.exitDialog.processing"),
                                        new ExitRequest(), (event) -> {
                                            // Exit to main menu
                                            ContextHelper.runInFxThreadStatic(() -> {
                                                hideOverlay(0, null);
                                                application.gui.loginScene.clearPassword();
                                                try {
                                                    application.saveSettings();
                                                    application.runtimeStateMachine.exit();
                                                    getCurrentStage().setScene(application.gui.loginScene);
                                                } catch (Exception ex) {
                                                    LogHelper.error(ex);
                                                }
                                            });
                                        }, (event) -> {

                                        }), () -> {
                        }, true));
        LookupHelper.<ButtonBase>lookup(layout, "#clientLaunch").setOnAction((e) -> launchClient());
        CommonHelper.newThread("SkinHead Downloader Thread", true, () -> {
            try {
                updateSkinHead();
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        });
    }

    private void updateSkinHead() throws IOException {
        PlayerProfile playerProfile = application.runtimeStateMachine.getPlayerProfile();
        if (playerProfile == null || playerProfile.skin == null || playerProfile.skin.url == null)
            return;
        String url = playerProfile.skin.url;
        BufferedImage image = downloadSkinHead(url);
        avatar.setImage(convertToFxImage(image));
    }

    private static Image convertToFxImage(BufferedImage image) {
        if (JVMHelper.JVM_VERSION >= 9) {
            return SwingFXUtils.toFXImage(image, null);
        } else {
            return convertToFxImageJava8(image);
        }
    }

    private static Image convertToFxImageJava8(BufferedImage image) {
        int bw = image.getWidth();
        int bh = image.getHeight();
        switch (image.getType()) {
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
                break;
            default:
                BufferedImage converted = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D graphics2D = converted.createGraphics();
                graphics2D.drawImage(image, 0, 0, null);
                graphics2D.dispose();
                image = converted;
                break;
        }
        WritableImage writableImage = new WritableImage(bw, bh);
        PixelWriter pixelWriter = writableImage.getPixelWriter();
        IntegerComponentRaster integerComponentRaster = (IntegerComponentRaster) image.getRaster();
        int[] data = integerComponentRaster.getDataStorage();
        int offset = integerComponentRaster.getDataOffset(0);
        int scan = integerComponentRaster.getScanlineStride();
        PixelFormat<IntBuffer> pf = (image.isAlphaPremultiplied() ?
                PixelFormat.getIntArgbPreInstance() :
                PixelFormat.getIntArgbInstance());
        pixelWriter.setPixels(0, 0, bw, bh, pf, data, offset, scan);
        return writableImage;
    }

    private BufferedImage downloadSkinHead(String url) throws IOException {
        BufferedImage image = ImageIO.read(new URL(url));
        int width = image.getWidth();
        int renderScale = width / 64;
        int offset = 4 * renderScale;
        return image.getSubimage(offset, offset, offset, offset);
    }

    private void changeServer(ClientProfile profile, ServerPinger.Result pingerResult) {
        application.runtimeStateMachine.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
        LookupHelper.<Text>lookup(layout, "#heading").setText(profile.getTitle());
        LookupHelper.<Text>lookup(LookupHelper.
                        <ScrollPane>lookup(layout, "#serverInfo").getContent(),
                "#servertext").setText(profile.getInfo());
        if (pingerResult != null)
            LookupHelper.<Text>lookup(layout, "#headingOnline").setText(String.format("%d / %d", pingerResult.onlinePlayers, pingerResult.maxPlayers));
        else
            LookupHelper.<Text>lookup(layout, "#headingOnline").setText("? / ?");
    }

    private void launchClient() {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        if (profile == null)
            return;
        processRequest(application.getTranslation("runtime.overlay.processing.text.setprofile"), new SetProfileRequest(profile), (result) -> showOverlay(application.gui.updateOverlay, (e) -> {
            Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
            LogHelper.info("Start update to %s", target.toString());
            application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.assets"));
            application.gui.updateOverlay.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), profile, false, (assetHDir) -> {
                Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
                LogHelper.info("Start update to %s", targetClient.toString());
                application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.client"));
                application.gui.updateOverlay.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), profile, true, (clientHDir) -> {
                    LogHelper.info("Success update");
                    application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.launch"));
                    doLaunchClient(target, assetHDir, targetClient, clientHDir, profile);
                });
            });
        }), null);
    }

    private void doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir, ClientProfile profile) {
        ClientLauncherProcess clientLauncherProcess = new ClientLauncherProcess(clientDir, assetDir, profile, application.runtimeStateMachine.getPlayerProfile(),
                application.runtimeStateMachine.getAccessToken(), clientHDir, assetHDir, assetHDir/* Replace to jvmHDir */);
        clientLauncherProcess.params.ram = application.runtimeSettings.ram;
        if (clientLauncherProcess.params.ram > 0) {
            clientLauncherProcess.jvmArgs.add("-Xms" + clientLauncherProcess.params.ram + 'M');
            clientLauncherProcess.jvmArgs.add("-Xmx" + clientLauncherProcess.params.ram + 'M');
        }
        clientLauncherProcess.params.fullScreen = application.runtimeSettings.fullScreen;
        clientLauncherProcess.params.autoEnter = application.runtimeSettings.autoEnter;
        contextHelper.runCallback(() -> {
            Thread writerThread = CommonHelper.newThread("Client Params Writer Thread", true, () -> {
                try {
                    clientLauncherProcess.runWriteParams(new InetSocketAddress("127.0.0.1", Launcher.getConfig().clientPort));
                    if (!application.runtimeSettings.debug) {
                        LogHelper.debug("Params writted successful. Exit...");
                        LauncherEngine.exitLauncher(0);
                    }
                } catch (Throwable e) {
                    LogHelper.error(e);
                    if (getCurrentOverlay() instanceof DebugOverlay) {
                        DebugOverlay debugOverlay = (DebugOverlay) getCurrentOverlay();
                        debugOverlay.append(String.format("Launcher fatal error(Write Params Thread): %s: %s", e.getClass().getName(), e.getMessage()));
                        if (debugOverlay.currentProcess != null && debugOverlay.currentProcess.isAlive()) {
                            debugOverlay.currentProcess.destroy();
                        }
                    }
                }
            });
            writerThread.start();
            application.gui.debugOverlay.writeParametersThread = writerThread;
            clientLauncherProcess.start(true);
            showOverlay(application.gui.debugOverlay, (e) -> application.gui.debugOverlay.onProcess(clientLauncherProcess.getProcess()));
        }).run();
    }
}
