package pro.gravit.launcher.client.gui.scene;

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
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.request.auth.ExitRequest;
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

    public ServerInfoScene(JavaFXApplication application) {
        super("scenes/serverinfo/serverinfo.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<Button>lookup(header, "#back").setOnAction((e) -> {
            try {
                switchScene(application.gui.serverMenuScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });

        LookupHelper.<ButtonBase>lookup(header, "#controls", "#clientSettings").setOnAction((e) -> {
            try {
                if (application.runtimeStateMachine.getProfile() == null)
                    return;
                switchScene(application.gui.optionsScene);
                application.gui.optionsScene.reset();
                application.gui.optionsScene.addProfileOptionals(application.runtimeStateMachine.getOptionalView());
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#settings").setOnAction((e) -> {
            try {
                switchScene(application.gui.settingsScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#deauth").setOnAction((e) ->
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
                                                    switchScene(application.gui.loginScene);
                                                } catch (Exception ex) {
                                                    errorHandle(ex);
                                                }
                                            });
                                        }, (event) -> {

                                        }), () -> {
                        }, true));
        reset();
    }

    @Override
    public void reset() {
        avatar.setImage(originalAvatarImage);
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        LookupHelper.<Label>lookupIfPossible(layout, "#serverName").ifPresent((e) -> e.setText(profile.getTitle()));
        LookupHelper.<Label>lookupIfPossible(layout, "#serverDescription").ifPresent((e) -> e.setText(profile.getInfo()));
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname").ifPresent((e) -> e.setText(application.runtimeStateMachine.getUsername()));
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ServerMenuScene.getServerButton(application, profile).thenAccept(pane -> {
            contextHelper.runInFxThread(() -> {
                Button save = LookupHelper.lookup(pane,  "#save");
                save.setVisible(true);
                save.setText("GAME");
                save.setOnAction((e) -> launchClient());
                serverButtonContainer.getChildren().add(pane);
            });
        });
        ServerMenuScene.putAvatarToImageView(application, application.runtimeStateMachine.getUsername(), avatar);
    }

    @Override
    public String getName() {
        return null;
    }

    private void downloadClients(ClientProfile profile, Path jvmDir, HashedDir jvmHDir)
    {
        Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
        LogHelper.info("Start update to %s", target.toString());
        application.gui.updateScene.initNewPhase(application.getTranslation("runtime.overlay.update.phase.assets"));
        application.gui.updateScene.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), false, (assetHDir) -> {
            Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
            LogHelper.info("Start update to %s", targetClient.toString());
            application.gui.updateScene.initNewPhase(application.getTranslation("runtime.overlay.update.phase.client"));
            application.gui.updateScene.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), true, (clientHDir) -> {
                LogHelper.info("Success update");
                application.gui.updateScene.initNewPhase(application.getTranslation("runtime.overlay.update.phase.launch"));
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
                    if (getCurrentStage().getScene() instanceof DebugScene) { //TODO: FIX
                        DebugScene debugScene = (DebugScene) getCurrentStage().getScene();
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
        }).run();
    }

    private boolean isEnabledDownloadJava()
    {
        return application.securityService.isMayBeDownloadJava() && application.guiModuleConfig.enableDownloadJava && (!application.guiModuleConfig.userDisableDownloadJava || application.runtimeSettings.disableJavaDownload);
    }
    private void launchClient() {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        if (profile == null)
            return;
        processRequest(application.getTranslation("runtime.overlay.processing.text.setprofile"), new SetProfileRequest(profile), (result) -> contextHelper.runInFxThread(() -> {
            switchScene(application.gui.updateScene);
            application.gui.updateScene.initNewPhase(application.getTranslation("runtime.overlay.update.phase.java"));
            if(isEnabledDownloadJava())
            {
                String jvmDirName = JVMHelper.OS_BITS == 64 ? application.guiModuleConfig.jvmWindows64Dir : application.guiModuleConfig.jvmWindows32Dir;
                Path jvmDirPath = DirBridge.dirUpdates.resolve(jvmDirName);
                application.gui.updateScene.sendUpdateRequest( jvmDirName, jvmDirPath, null, profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), false, (jvmHDir) -> {
                    downloadClients(profile, jvmDirPath, jvmHDir);
                });
            }
            else
            {
                downloadClients(profile, null, null);
            }
        }), null);
    }
}
