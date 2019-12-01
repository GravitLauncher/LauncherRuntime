package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import pro.gravit.launcher.client.ClientLauncher;
import pro.gravit.launcher.client.DirBridge;
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
import java.util.concurrent.TimeUnit;

public class ServerMenuScene extends AbstractScene {
    public static String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    public Node layout;
    private Node lastSelectedServerButton;
    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() throws Exception {
        layout = LookupHelper.lookup(scene.getRoot(),  "#layout", "#serverMenu");
        sceneBaseInit(layout);
        ((Labeled)layout.lookup("#nickname")).setText(application.runtimeStateMachine.getUsername());
        Map<ClientProfile, Future<Pane>> futures = new HashMap<>();
        for(ClientProfile profile : application.runtimeStateMachine.getProfiles())
        {
            futures.put(profile, application.getNoCacheFxml(SERVER_BUTTON_FXML));
        }
        Pane serverList = (Pane) ((ScrollPane)layout.lookup("#serverlist")).getContent();
        futures.forEach((profile, future) -> {
            try {
                Pane pane = future.get();
                ((Text)pane.lookup("#nameServer")).setText(profile.getTitle());
                ((Text)pane.lookup("#genreServer")).setText(profile.getVersion().toString());
                pane.setOnMouseClicked((e) -> {
                    if(!e.getButton().equals(MouseButton.PRIMARY)) return;
                    if(lastSelectedServerButton != null) lastSelectedServerButton.getStyleClass().remove("nameServerSelected");
                    lastSelectedServerButton = pane.lookup("#nameServer");
                    lastSelectedServerButton.getStyleClass().add("nameServerSelected");
                    lastSelectedServerButton.getStyleClass().forEach((t) -> LogHelper.debug("SClass %s", t));
                    changeServer(profile);
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                });
                serverList.getChildren().add(pane);
            } catch (InterruptedException | ExecutionException e) {
                LogHelper.error(e);
            }
        });
        ((ButtonBase)layout.lookup("#clientSettings")).setOnAction((e) -> {
            try {
                application.setMainScene(application.gui.optionsScene);
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        ((ButtonBase)layout.lookup("#settings")).setOnAction((e) -> {
            try {
                application.setMainScene(application.gui.settingsScene);
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        ((ButtonBase)layout.lookup("#clientLaunch")).setOnAction((e) -> {
            launchClient();
        });
    }
    public void changeServer(ClientProfile profile)
    {
        application.runtimeStateMachine.setProfile(profile);
        ((Text)layout.lookup("#heading")).setText(profile.getTitle());
        ((Text)((ScrollPane)layout.lookup("#serverInfo")).getContent().lookup("#servertext")).setText(profile.getInfo());
    }
    public void launchClient()
    {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        if(profile == null) return;
        processRequest("Set profile", new SetProfileRequest(profile), (result) -> {
            showOverlay(application.gui.updateOverlay, (e) -> {
                Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
                LogHelper.info("Start update to %s", target.toString());
                application.gui.updateOverlay.initNewPhase("Скачивание файлов ассетов");
                application.gui.updateOverlay.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), profile, false, (assetHDir) -> {
                    Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
                    LogHelper.info("Start update to %s", targetClient.toString());
                    application.gui.updateOverlay.initNewPhase("Скачивание файлов клиента");
                    application.gui.updateOverlay.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), profile, true, (clientHDir) -> {
                        LogHelper.info("Success update");
                        application.gui.updateOverlay.initNewPhase("Запуск клиента");
                        doLaunchClient(target, assetHDir, targetClient, clientHDir, profile);
                    });
                });
            });
        }, null);
    }
    public void doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir, ClientProfile profile)
    {
        ClientLauncher.Params clientParams = new ClientLauncher.Params(null, assetDir, clientDir, application.runtimeStateMachine.getPlayerProfile(), application.runtimeStateMachine.getAccessToken(),
            false /* TODO */, false /* TODO */, application.settings.ram, 0,0);
        contextHelper.runCallback(() -> {
            Process process = ClientLauncher.launch(assetHDir, clientHDir, profile, clientParams, true);
            showOverlay(application.gui.debugOverlay, (e) -> {
                application.gui.debugOverlay.onProcess(process);
            });
        }).run();

    }
}
