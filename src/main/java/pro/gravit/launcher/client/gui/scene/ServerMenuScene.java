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
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.ClientLauncher;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ServerMenuScene extends AbstractScene {
    public static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    public static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    public Node layout;
    public ImageView avatar;
    private Node lastSelectedServerButton;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(), "#layout", "#serverMenu");
        sceneBaseInit(layout);
        ((Labeled) layout.lookup("#nickname")).setText(application.runtimeStateMachine.getUsername());
        avatar = (ImageView) layout.lookup("#avatar");
        Map<ClientProfile, Future<Pane>> futures = new HashMap<>();
        Map<ClientProfile, Integer> positionMap = new HashMap<>();
        {
            int pos = 0;
            for (ClientProfile profile : application.runtimeStateMachine.getProfiles()) {
                String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profile.getTitle());
                URL customFxml = application.tryResource(customFxmlName);
                if(customFxml != null)
                {
                    futures.put(profile, application.getNoCacheFxml(customFxmlName, IOHelper.newInput(customFxml)));
                }
                else
                {
                    futures.put(profile, application.getNoCacheFxml(SERVER_BUTTON_FXML));
                }
                positionMap.put(profile, pos);
                pos++;
            }
        }

        Pane serverList = (Pane) ((ScrollPane) layout.lookup("#serverlist")).getContent();
        futures.forEach((profile, future) -> {
            try {
                Pane pane = future.get();
                AtomicReference<ServerPinger.Result> pingerResult = new AtomicReference<>();
                ((Hyperlink) pane.lookup("#nameServer")).setText(profile.getTitle());
                ((Text) pane.lookup("#genreServer")).setText(profile.getVersion().toString());
                profile.updateOptionalGraph();
                EventHandler<? super MouseEvent> handle = (e) -> {
                    if (!e.getButton().equals(MouseButton.PRIMARY)) return;
                    if (lastSelectedServerButton != null) {
                        lastSelectedServerButton.getStyleClass().remove("serverButtonsActive");
                        //lastSelectedServerButton.lookup("#nameServer").getStyleClass().remove("nameServerActive");
                    }
                    lastSelectedServerButton = pane;
                    lastSelectedServerButton.getStyleClass().add("serverButtonsActive");
                    //lastSelectedServerButton.lookup("#nameServer").getStyleClass().add("nameServerActive");
                    changeServer(profile, pingerResult.get());
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                };
                pane.setOnMouseClicked(handle);
                pane.lookup("#nameServer").setOnMouseClicked(handle);
                pane.setOnMouseEntered((e) -> pane.lookup("#nameServer").getStyleClass().add("nameServerActive"));
                pane.setOnMouseExited((e) -> pane.lookup("#nameServer").getStyleClass().remove("nameServerActive"));
                ///////////
                int gettedPos = positionMap.get(profile);
                if(gettedPos >= serverList.getChildren().size()) gettedPos = serverList.getChildren().size();
                serverList.getChildren().add(gettedPos, pane);
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
                        ((Text) pane.lookup("#online")).setText(String.valueOf(finalResult.onlinePlayers));
                        if (application.runtimeStateMachine.getProfile() != null &&
                                application.runtimeStateMachine.getProfile() == profile) {
                            ((Text) layout.lookup("#headingOnline")).setText(String.format("%d / %d", finalResult.onlinePlayers, finalResult.maxPlayers));
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
        ((ButtonBase) layout.lookup("#clientSettings")).setOnAction((e) -> {
            try {
                if (application.runtimeStateMachine.getProfile() == null) return;
                application.setMainScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.runtimeStateMachine.getProfile());
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        ((ButtonBase) layout.lookup("#settings")).setOnAction((e) -> {
            try {
                application.setMainScene(application.gui.settingsScene);
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        ((ButtonBase) layout.lookup("#exit")).setOnAction((e) -> {
            application.messageManager.showApplyDialog(application.getLangResource("runtime.scenes.settings.exitDialog.header"),
                    application.getLangResource("runtime.scenes.settings.exitDialog.description"), () -> {
                        LogHelper.debug("Try exit");
                        processRequest(application.getLangResource("runtime.scenes.settings.exitDialog.processing"),
                                new ExitRequest(), (event) -> {
                                    //Exit to main menu
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
                                }, (ev) -> {

                                });
                    }, () -> {}, true);
        });
        ((ButtonBase) layout.lookup("#clientLaunch")).setOnAction((e) -> launchClient());
        CommonHelper.newThread("SkinHead Downloader Thread", true, () -> {
            try {
                updateSkinHead();
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        });
    }
    public void updateSkinHead() throws IOException
    {
        PlayerProfile playerProfile = application.runtimeStateMachine.getPlayerProfile();
        if(playerProfile == null || playerProfile.skin == null || playerProfile.skin.url == null) return;
        String url = playerProfile.skin.url;
        BufferedImage image = downloadSkinHead(url);
        avatar.setImage(convertToFxImage(image));
    }
    public static Image convertToFxImage(BufferedImage image)
    {
        if(JVMHelper.JVM_VERSION >= 9)
        {
            return SwingFXUtils.toFXImage(image, null);
        }
        else
        {
            return convertToFxImageJava8(image);
        }
    }
    private static Image convertToFxImageJava8(BufferedImage image) { //Very slow!
        WritableImage wr = null;
        if (image != null) {
            wr = new WritableImage(image.getWidth(), image.getHeight());
            PixelWriter pw = wr.getPixelWriter();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    pw.setArgb(x, y, image.getRGB(x, y));
                }
            }
        }

        return new ImageView(wr).getImage();
    }
    public BufferedImage downloadSkinHead(String url) throws IOException
    {
        BufferedImage image = ImageIO.read(new URL(url));
        int height = image.getHeight();
        int width = image.getWidth();
        int renderScale = width / 64;
        if(height == width)
        {
            height /= 2; // slim skin
        }
        int offset = 4*renderScale;
        return image.getSubimage(offset, offset, offset, offset);
    }

    public void changeServer(ClientProfile profile, ServerPinger.Result pingerResult) {
        application.runtimeStateMachine.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
        ((Text) layout.lookup("#heading")).setText(profile.getTitle());
        ((Text) ((ScrollPane) layout.lookup("#serverInfo")).getContent().lookup("#servertext")).setText(profile.getInfo());
        if (pingerResult != null)
            ((Text) layout.lookup("#headingOnline")).setText(String.format("%d / %d", pingerResult.onlinePlayers, pingerResult.maxPlayers));
        else
            ((Text) layout.lookup("#headingOnline")).setText("? / ?");
    }

    public void launchClient() {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        if (profile == null) return;
        processRequest(application.getLangResource("runtime.overlay.processing.text.setprofile"), new SetProfileRequest(profile), (result) -> showOverlay(application.gui.updateOverlay, (e) -> {
            Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
            LogHelper.info("Start update to %s", target.toString());
            application.gui.updateOverlay.initNewPhase(application.getLangResource("runtime.overlay.update.phase.assets"));
            application.gui.updateOverlay.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), profile, false, (assetHDir) -> {
                Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
                LogHelper.info("Start update to %s", targetClient.toString());
                application.gui.updateOverlay.initNewPhase(application.getLangResource("runtime.overlay.update.phase.client"));
                application.gui.updateOverlay.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), profile, true, (clientHDir) -> {
                    LogHelper.info("Success update");
                    application.gui.updateOverlay.initNewPhase(application.getLangResource("runtime.overlay.update.phase.launch"));
                    doLaunchClient(target, assetHDir, targetClient, clientHDir, profile);
                });
            });
        }), null);
    }

    public void doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir, ClientProfile profile) {
        //ClientLauncher.Params clientParams = new ClientLauncher.Params(null, assetDir, clientDir, application.runtimeStateMachine.getPlayerProfile(), application.runtimeStateMachine.getAccessToken(),
        //        application.runtimeSettings.autoEnter, application.runtimeSettings.fullScreen, application.runtimeSettings.ram, 0, 0);
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
                    if(!application.runtimeSettings.debug)
                    {
                        LogHelper.debug("Params writted successful. Exit...");
                        LauncherEngine.exitLauncher(0);
                    }
                } catch (Throwable e) {
                    LogHelper.error(e);
                    if(getCurrentOverlay() instanceof DebugOverlay)
                    {
                        DebugOverlay debugOverlay = (DebugOverlay) getCurrentOverlay();
                        debugOverlay.append(String.format("Launcher fatal error(Write Params Thread): %s: %s", e.getClass().getName(), e.getMessage()));
                        if(debugOverlay.currentProcess != null && debugOverlay.currentProcess.isAlive())
                        {
                            debugOverlay.currentProcess.destroy();
                        }
                    }
                }
            });
            writerThread.start();
            application.gui.debugOverlay.writeParamsThread = writerThread;
            clientLauncherProcess.start(true);
            showOverlay(application.gui.debugOverlay, (e) -> application.gui.debugOverlay.onProcess(clientLauncherProcess.getProcess()));
        }).run();

    }
}
