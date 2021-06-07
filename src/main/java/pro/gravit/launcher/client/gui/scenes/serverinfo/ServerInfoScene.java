package pro.gravit.launcher.client.gui.scenes.serverinfo;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.ClientLauncherProcess;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerButtonComponent;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.client.gui.scenes.debug.DebugScene;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.request.auth.SetProfileRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerInfoScene extends AbstractScene {
    private ImageView avatar;
    private Image originalAvatarImage;
    private ServerButtonComponent serverButton;

    public ServerInfoScene(JavaFXApplication application) {
        super("scenes/serverinfo/serverinfo.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent(
                (h) -> {
                    try {
                        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(h.getFitWidth(), h.getFitHeight());
                        clip.setArcWidth(h.getFitWidth());
                        clip.setArcHeight(h.getFitHeight());
                        h.setClip(clip);
                        h.setImage(originalAvatarImage);
                    } catch (Throwable e) {
                        LogHelper.warning("Skin head error");
                    }
                }
        );
        LookupHelper.<Button>lookup(header, "#back").setOnAction((e) -> {
            try {
                switchScene(application.gui.serverMenuScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });

        LookupHelper.<ButtonBase>lookup(header, "#controls", "#clientSettings").setOnAction((e) -> {
            try {
                if (application.stateService.getProfile() == null)
                    return;
                switchScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.stateService.getOptionalView());
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.settingsScene);
                application.gui.settingsScene.reset();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        reset();
    }

    @Override
    public void reset() {
        avatar.setImage(originalAvatarImage);
        ClientProfile profile = application.stateService.getProfile();
        LookupHelper.<Label>lookupIfPossible(layout, "#serverName").ifPresent((e) -> e.setText(profile.getTitle()));
        LookupHelper.<Label>lookupIfPossible(layout, "#serverDescription").ifPresent((e) -> e.setText(profile.getInfo()));
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname").ifPresent((e) -> e.setText(application.stateService.getUsername()));
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        serverButton = ServerMenuScene.getServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(application.getTranslation("runtime.scenes.serverinfo.serverButton.game"), (e) -> launchClient());
        ServerMenuScene.putAvatarToImageView(application, application.stateService.getUsername(), avatar);
    }

    @Override
    public String getName() {
        return null;
    }

    private void downloadClients(ClientProfile profile, Path jvmDir, HashedDir jvmHDir)
    {
        Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
        LogHelper.info("Start update to %s", target.toString());
        application.gui.updateScene.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), application.stateService.getOptionalView(), false, (assetHDir) -> {
            Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
            LogHelper.info("Start update to %s", targetClient.toString());
            application.gui.updateScene.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), application.stateService.getOptionalView(), true, (clientHDir) -> {
                LogHelper.info("Success update");
                doLaunchClient(target, assetHDir, targetClient, clientHDir, profile, application.stateService.getOptionalView(), jvmDir, jvmHDir);
            });
        });
    }

    private void doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir, ClientProfile profile, OptionalView view, Path jvmDir, HashedDir jvmHDir) {
        RuntimeSettings.ProfileSettings profileSettings = application.getProfileSettings();
        ClientLauncherProcess clientLauncherProcess = new ClientLauncherProcess(clientDir, assetDir,
                jvmDir != null ? jvmDir : ( profileSettings.javaPath == null ? Paths.get(System.getProperty("java.home")) : Paths.get(profileSettings.javaPath) ),
                clientDir.resolve("resourcepacks"), profile, application.stateService.getPlayerProfile(), view,
                application.stateService.getAccessToken(), clientHDir, assetHDir, jvmHDir);
        clientLauncherProcess.params.ram = profileSettings.ram;
        if (clientLauncherProcess.params.ram > 0) {
            clientLauncherProcess.jvmArgs.add("-Xms" + clientLauncherProcess.params.ram + 'M');
            clientLauncherProcess.jvmArgs.add("-Xmx" + clientLauncherProcess.params.ram + 'M');
        }
        clientLauncherProcess.params.fullScreen = profileSettings.fullScreen;
        clientLauncherProcess.params.autoEnter = profileSettings.autoEnter;
        contextHelper.runCallback(() -> {
            Thread writerThread = CommonHelper.newThread("Client Params Writer Thread", true, () -> {
                try {
                    clientLauncherProcess.runWriteParams(new InetSocketAddress("127.0.0.1", Launcher.getConfig().clientPort));
                    if (!profileSettings.debug) {
                        LogHelper.debug("Params writted successful. Exit...");
                        LauncherEngine.exitLauncher(0);
                    }
                } catch (Throwable e) {
                    LogHelper.error(e);
                    if (getCurrentStage().getVisualComponent() instanceof DebugScene) { //TODO: FIX
                        DebugScene debugScene = (DebugScene) getCurrentStage().getVisualComponent();
                        debugScene.append(String.format("Launcher fatal error(Write Params Thread): %s: %s", e.getClass().getName(), e.getMessage()));
                        if (debugScene.currentProcess != null && debugScene.currentProcess.isAlive()) {
                            debugScene.currentProcess.destroy();
                        }
                    }
                }
            });
            writerThread.start();
            application.gui.debugScene.writeParametersThread = writerThread;
            clientLauncherProcess.start(true);
            contextHelper.runInFxThread(() -> {
                switchScene(application.gui.debugScene);
                application.gui.debugScene.onProcess(clientLauncherProcess.getProcess());
            });
        });
    }

    private boolean isEnabledDownloadJava()
    {
        return application.securityService.isMayBeDownloadJava() && application.guiModuleConfig.enableDownloadJava && (!application.guiModuleConfig.userDisableDownloadJava || application.runtimeSettings.disableJavaDownload);
    }
    private void launchClient() {
        ClientProfile profile = application.stateService.getProfile();
        if (profile == null)
            return;
        processRequest(application.getTranslation("runtime.overlay.processing.text.setprofile"), new SetProfileRequest(profile), (result) -> contextHelper.runInFxThread(() -> {
            hideOverlay(0, (ev) -> {
                try {
                    switchScene(application.gui.updateScene);
                } catch (Exception e) {
                    errorHandle(e);
                }
                if(isEnabledDownloadJava())
                {
                    String jvmDirName = JVMHelper.OS_BITS == 64 ? application.guiModuleConfig.jvmWindows64Dir : application.guiModuleConfig.jvmWindows32Dir;
                    Path jvmDirPath = DirBridge.dirUpdates.resolve(jvmDirName);
                    application.gui.updateScene.sendUpdateRequest( jvmDirName, jvmDirPath, null, profile.isUpdateFastCheck(), application.stateService.getOptionalView(), false, (jvmHDir) -> {
                        downloadClients(profile, jvmDirPath, jvmHDir);
                    });
                }
                else
                {
                    downloadClients(profile, null, null);
                }
            });
        }), null);
    }
}
