package pro.gravit.launcher.client.gui.scene;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
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
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.launcher.request.auth.SetProfileRequest;
import pro.gravit.launcher.request.management.PingServerRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ServerMenuScene extends AbstractScene {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private static final String SERVER_BUTTON_CUSTOM_IMAGE = "images/servers/%s.png";
    private Node layout;
    private ImageView avatar;
    private ImageView serverImage;
    private Node lastSelectedServerButton;

    private List<ClientProfile> lastProfiles;
    private Image originalAvatarImage;
    private Image originalServerImage;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
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
        DataBufferInt raster = (DataBufferInt) image.getRaster().getDataBuffer();
        int scan = image.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel
                ? ((SinglePixelPackedSampleModel) image.getRaster().getSampleModel()).getScanlineStride() : 0;
        PixelFormat<IntBuffer> pf = image.isAlphaPremultiplied() ?
                PixelFormat.getIntArgbPreInstance() :
                PixelFormat.getIntArgbInstance();
        writableImage.getPixelWriter().setPixels(0, 0, bw, bh, pf, raster.getData(), raster.getOffset(), scan);
        return writableImage;
    }

    @Override
    public void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(), "#layout", "#serverMenu");
        sceneBaseInit(layout);
        avatar = LookupHelper.lookup(layout, "#avatar");
        serverImage = LookupHelper.lookup(layout, "#serverImage");
        originalAvatarImage = avatar.getImage();
        originalServerImage = serverImage.getImage();

        LookupHelper.<ButtonBase>lookup(layout, "#clientSettings").setOnAction((e) -> {
            try {
                if (application.runtimeStateMachine.getProfile() == null)
                    return;
                showOverlay(application.gui.optionsOverlay, (ec) -> {
                    application.gui.optionsOverlay.addProfileOptionals(application.runtimeStateMachine.getOptionalView());
                });
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#settings").setOnAction((e) -> {
            showOverlay(application.gui.settingsOverlay, null);
        });
        LookupHelper.<ButtonBase>lookup(layout, "#exit").setOnAction((e) ->
                application.messageManager.showApplyDialog(application.getTranslation("runtime.overlay.settings.exitDialog.header"),
                        application.getTranslation("runtime.overlay.settings.exitDialog.description"), () ->
                                processRequest(application.getTranslation("runtime.overlay.settings.exitDialog.processing"),
                                        new ExitRequest(), (event) -> {
                                            // Exit to main menu
                                            ContextHelper.runInFxThreadStatic(() -> {
                                                hideOverlay(0, null);
                                                application.gui.loginScene.clearPassword();
                                                application.gui.loginScene.reset();
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
        reset();
    }
    class ServerButtonCache
    {
        public Future<Pane> pane;
        SoftReference<Image> imageRef = new SoftReference<>(null);
        public int position;
        Supplier<Image> getImage = () -> originalServerImage;
        public Image getImage()
        {
            Image result = imageRef.get();
            if(result != null)
            {
                return result;
            }
            result = getImage.get();
            imageRef = new SoftReference<>(result);
            return result;
        }
    }

    @Override
    public void reset() {
        lastProfiles = application.runtimeStateMachine.getProfiles();
        Map<ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        LookupHelper.<Labeled>lookup(layout, "#nickname").setText(application.runtimeStateMachine.getUsername());
        avatar.setImage(originalAvatarImage);
        try {
            int position = 0;
            for (ClientProfile profile : application.runtimeStateMachine.getProfiles()) {
                ServerButtonCache cache = new ServerButtonCache();
                UUID profileUUID = profile.getUUID();
                if(profileUUID == null) {
                    profileUUID = UUID.randomUUID();
                    LogHelper.warning("Profile %s UUID null", profileUUID);
                }
                String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profileUUID);
                URL customFxml = application.tryResource(customFxmlName);
                if (customFxml != null) {
                    cache.pane = application.getNonCachedFxmlAsync(customFxmlName, IOHelper.newInput(customFxml));
                } else {
                    cache.pane = application.getNonCachedFxmlAsync(SERVER_BUTTON_FXML);
                }
                String customImageName = String.format(SERVER_BUTTON_CUSTOM_IMAGE, profileUUID);
                URL customImage = application.tryResource(customImageName);
                if(customImage != null)
                {
                    cache.getImage = () -> new Image(customImage.toString());
                }
                cache.position = position;
                serverButtonCacheMap.put(profile, cache);
                position++;
            }
        } catch (IOException e)
        {
            errorHandle(e);
            return;
        }

        Pane serverList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#serverlist").getContent();
        serverList.getChildren().clear();
        application.runtimeStateMachine.clearServerPingCallbacks();
        serverButtonCacheMap.forEach((profile, serverButtonCache) -> {
            try {
                Pane pane = serverButtonCache.pane.get();
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
                    changeServer(profile, pingerResult.get(), serverButtonCache.getImage());
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                };
                pane.setOnMouseClicked(handle);
                LookupHelper.lookup(pane, "#nameServer").setOnMouseClicked(handle);
                ///////////
                int profilePosition = serverButtonCache.position;
                if (profilePosition >= serverList.getChildren().size())
                    profilePosition = serverList.getChildren().size();
                serverList.getChildren().add(profilePosition, pane);
                ///////////
                ClientProfile.ServerProfile defaultServer = profile.getDefaultServerProfile();
                if(defaultServer == null || defaultServer.serverAddress != null)
                {
                    application.workers.submit(() -> {
                        ServerPinger pinger = new ServerPinger(profile);
                        ServerPinger.Result result;
                        try {
                            result = pinger.ping();
                        } catch (IOException e) {
                            result = new ServerPinger.Result(0, 0, "0 / 0");
                        }
                        pingerResult.set(result);
                        changeOnline(pane, profile, result.onlinePlayers, result.maxPlayers);
                    });
                }
                else if(profile.getServers() != null)
                {
                    for(ClientProfile.ServerProfile serverProfile : profile.getServers())
                    {
                        if(serverProfile.isDefault)
                        {
                            application.runtimeStateMachine.addServerPingCallback(serverProfile.name, (report) -> {
                                changeOnline(pane, profile, report.playersOnline, report.maxPlayers);
                            });
                        }
                    }
                }
                if ((application.runtimeSettings.lastProfile == null && lastSelectedServerButton == null) || (profile.getUUID() != null && profile.getUUID().equals(application.runtimeSettings.lastProfile))) {
                    lastSelectedServerButton = pane;
                    lastSelectedServerButton.getStyleClass().add("serverButtonsActive");
                    changeServer(profile, pingerResult.get(), serverButtonCache.getImage());
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                }
            } catch (InterruptedException | ExecutionException e) {
                LogHelper.error(e);
            }
        });
        try {
            Request.service.request(new PingServerRequest()).thenAccept((event) -> {
                if(event.serverMap != null)
                {
                    event.serverMap.forEach((name, value) -> {
                        application.runtimeStateMachine.setServerPingReport(event.serverMap);
                    });
                }
            }).exceptionally((ex) -> {
                LogHelper.error(ex.getCause());
                return null;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        CommonHelper.newThread("SkinHead Downloader Thread", true, () -> {
            try {
                updateSkinHead();
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        }).start();
    }

    public void changeOnline(Pane pane, ClientProfile profile, int online, int maxOnline)
    {
        contextHelper.runInFxThread(() -> {
            LookupHelper.<Text>lookup(pane, "#online").setText(String.valueOf(online));
            if (application.runtimeStateMachine.getProfile() != null &&
                    application.runtimeStateMachine.getProfile() == profile) {
                LookupHelper.<Text>lookup(layout, "#headingOnline").setText(String.format("%d / %d", online, maxOnline));
            }
        });
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    @Override
    protected void doShow() {
        super.doShow();
        if(lastProfiles != application.runtimeStateMachine.getProfiles())
        {
            reset();
        }
    }

    private void updateSkinHead() throws IOException {
        PlayerProfile playerProfile = application.runtimeStateMachine.getPlayerProfile();
        if (playerProfile == null)
            return;
        if (playerProfile.skin == null || playerProfile.skin.url == null) {
            LogHelper.debug("Skin not found");
            return;
        }
        String url = playerProfile.skin.url;
        BufferedImage origImage = downloadSkinHead(url);
        int imageHeight = (int) avatar.getFitHeight(), imageWidth = (int) avatar.getFitWidth();
        java.awt.Image resized = origImage.getScaledInstance(imageWidth, imageHeight, java.awt.Image.SCALE_FAST);
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics2D = image.createGraphics();
        graphics2D.drawImage(resized, 0, 0, null);
        graphics2D.dispose();
        avatar.setImage(convertToFxImage(image));
    }

    private BufferedImage downloadSkinHead(String url) throws IOException {
        BufferedImage image = ImageIO.read(new URL(url));
        int width = image.getWidth();
        int renderScale = width / 64;
        int offset = 8 * renderScale;
        LogHelper.debug("ShinHead debug: W: %d Scale: %d Offset: %d", width, renderScale, offset);
        return image.getSubimage(offset, offset, offset, offset);
    }

    private void changeServer(ClientProfile profile, ServerPinger.Result pingerResult, Image serverImage) {
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
        if(serverImage != null)
        {
            this.serverImage.setImage(serverImage);
        }
    }
    private boolean isEnabledDownloadJava()
    {
        return application.securityService.isMayBeDownloadJava() && application.guiModuleConfig.enableDownloadJava && (!application.guiModuleConfig.userDisableDownloadJava || application.runtimeSettings.disableJavaDownload);
    }
    private void launchClient() {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        if (profile == null)
            return;
        processRequest(application.getTranslation("runtime.overlay.processing.text.setprofile"), new SetProfileRequest(profile), (result) -> showOverlay(application.gui.updateOverlay, (e) -> {
            application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.java"));
            if(isEnabledDownloadJava())
            {
                String jvmDirName = JVMHelper.OS_BITS == 64 ? application.guiModuleConfig.jvmWindows64Dir : application.guiModuleConfig.jvmWindows32Dir;
                Path jvmDirPath = DirBridge.dirUpdates.resolve(jvmDirName);
                application.gui.updateOverlay.sendUpdateRequest( jvmDirName, jvmDirPath, null, profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), false, (jvmHDir) -> {
                    downloadClients(profile, jvmDirPath, jvmHDir);
                });
            }
            else
            {
                downloadClients(profile, null, null);
            }
        }), null);
    }
    private void downloadClients(ClientProfile profile, Path jvmDir, HashedDir jvmHDir)
    {
        Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
        LogHelper.info("Start update to %s", target.toString());
        application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.assets"));
        application.gui.updateOverlay.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), false, (assetHDir) -> {
            Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
            LogHelper.info("Start update to %s", targetClient.toString());
            application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.client"));
            application.gui.updateOverlay.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), true, (clientHDir) -> {
                LogHelper.info("Success update");
                application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.launch"));
                doLaunchClient(target, assetHDir, targetClient, clientHDir, profile, application.runtimeStateMachine.getOptionalView(), jvmDir, jvmHDir);
            });
        });
    }

    private void doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir, ClientProfile profile, OptionalView view, Path jvmDir, HashedDir jvmHDir) {
        ClientLauncherProcess clientLauncherProcess = new ClientLauncherProcess(clientDir, assetDir, jvmDir != null ? jvmDir : Paths.get(System.getProperty("java.home")), clientDir.resolve("resourcepacks"), profile, application.runtimeStateMachine.getPlayerProfile(), view,
                application.runtimeStateMachine.getAccessToken(), clientHDir, assetHDir, jvmHDir);
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
