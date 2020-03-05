package pro.gravit.launcher.client.gui.scene;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.ClientLauncher;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.auth.SetProfileRequest;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ServerMenuScene extends AbstractScene {
    public static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    public Node layout;
    private Node lastSelectedServerButton;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(), "#layout", "#serverMenu");
        sceneBaseInit(layout);
        ((Labeled) layout.lookup("#nickname")).setText(application.runtimeStateMachine.getUsername());
        Map<ClientProfile, Future<Pane>> futures = new HashMap<>();
        Map<ClientProfile, Integer> positionMap = new HashMap<>();
        {
            int pos = 0;
            for (ClientProfile profile : application.runtimeStateMachine.getProfiles()) {
                futures.put(profile, application.getNoCacheFxml(SERVER_BUTTON_FXML));
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
        ((ButtonBase) layout.lookup("#clientLaunch")).setOnAction((e) -> launchClient());
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
        ClientLauncher.Params clientParams = new ClientLauncher.Params(null, assetDir, clientDir, application.runtimeStateMachine.getPlayerProfile(), application.runtimeStateMachine.getAccessToken(),
                application.runtimeSettings.autoEnter, application.runtimeSettings.fullScreen, application.runtimeSettings.ram, 0, 0);
        contextHelper.runCallback(() -> {
            Process process = ClientLauncher.launch(assetHDir, clientHDir, profile, clientParams, true);
            showOverlay(application.gui.debugOverlay, (e) -> application.gui.debugOverlay.onProcess(process));
        }).run();

    }
}
