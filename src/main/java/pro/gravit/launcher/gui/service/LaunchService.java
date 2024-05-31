package pro.gravit.launcher.gui.service;

import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.profiles.ClientProfileBuilder;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.impl.AbstractStage;
import pro.gravit.launcher.gui.impl.ContextHelper;
import pro.gravit.launcher.runtime.client.ClientLauncherProcess;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.launcher.core.hasher.HashedDir;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.base.profiles.ClientProfileVersions;
import pro.gravit.launcher.base.profiles.optional.OptionalView;
import pro.gravit.launcher.base.request.auth.SetProfileRequest;
import pro.gravit.utils.helper.*;

import java.io.EOFException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LaunchService {
    private final JavaFXApplication application;

    public LaunchService(JavaFXApplication application) {
        this.application = application;
    }

    public boolean isTestUpdate(ClientProfile profile, RuntimeSettings.ProfileSettings settings) {
        return application.offlineService.isOfflineMode() || (application.authService.checkDebugPermission("skipupdate") && settings.debugSkipUpdate);
    }

    private void downloadClients(CompletableFuture<ClientInstance> future, ClientProfile profile, RuntimeSettings.ProfileSettings settings, JavaHelper.JavaVersion javaVersion, HashedDir jvmHDir) {
        Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
        LogHelper.info("Start update to %s", target.toString());
        boolean testUpdate = isTestUpdate(profile, settings);
        Consumer<HashedDir> next = (assetHDir) -> {
            Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
            LogHelper.info("Start update to %s", targetClient.toString());
            application.gui.updateScene.sendUpdateRequest(profile.getDir(), targetClient,
                                                          profile.getClientUpdateMatcher(), true,
                                                          application.profilesService.getOptionalView(), true, testUpdate,
                                                          (clientHDir) -> {
                                                              LogHelper.info("Success update");
                                                              try {
                                                                  ClientInstance instance = doLaunchClient(target, assetHDir, targetClient,
                                                                                 clientHDir, profile,
                                                                                 application.profilesService.getOptionalView(),
                                                                                 javaVersion, jvmHDir);
                                                                  future.complete(instance);
                                                              } catch (Throwable e) {
                                                                  future.completeExceptionally(e);
                                                              }
                                                          });
        };
        if (profile.getVersion().compareTo(ClientProfileVersions.MINECRAFT_1_6_4) <= 0) {
            application.gui.updateScene.sendUpdateRequest(profile.getAssetDir(), target,
                                                          profile.getAssetUpdateMatcher(), true, null, false, testUpdate, next);
        } else {
            application.gui.updateScene.sendUpdateAssetRequest(profile.getAssetDir(), target,
                                                               profile.getAssetUpdateMatcher(), true,
                                                               profile.getAssetIndex(), testUpdate, next);
        }
    }

    private ClientInstance doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir,
            ClientProfile profile, OptionalView view, JavaHelper.JavaVersion javaVersion, HashedDir jvmHDir) {
        RuntimeSettings.ProfileSettings profileSettings = application.getProfileSettings();
        if (javaVersion == null) {
            javaVersion = application.javaService.getRecommendJavaVersion(profile);
        }
        if (javaVersion == null) {
            javaVersion = JavaHelper.JavaVersion.getCurrentJavaVersion();
        }
        if(application.authService.checkDebugPermission("skipfilemonitor") && profileSettings.debugSkipFileMonitor) {
            var builder = new ClientProfileBuilder(profile);
            builder.setUpdate(new ArrayList<>());
            builder.setUpdateVerify(new ArrayList<>());
            builder.setUpdateExclusions(new ArrayList<>());
            profile = builder.createClientProfile();
        }
        ClientLauncherProcess clientLauncherProcess =
                new ClientLauncherProcess(clientDir, assetDir, javaVersion, clientDir.resolve("resourcepacks"), profile,
                                          application.authService.getPlayerProfile(), view,
                                          application.authService.getAccessToken(), clientHDir, assetHDir, jvmHDir);
        clientLauncherProcess.params.ram = profileSettings.ram;
        clientLauncherProcess.params.offlineMode = application.offlineService.isOfflineMode();
        if (clientLauncherProcess.params.ram > 0) {
            clientLauncherProcess.jvmArgs.add("-Xms" + clientLauncherProcess.params.ram + 'M');
            clientLauncherProcess.jvmArgs.add("-Xmx" + clientLauncherProcess.params.ram + 'M');
        }
        clientLauncherProcess.params.fullScreen = profileSettings.fullScreen;
        clientLauncherProcess.params.autoEnter = profileSettings.autoEnter;
        if(JVMHelper.OS_TYPE == JVMHelper.OS.LINUX) {
            clientLauncherProcess.params.lwjglGlfwWayland = profileSettings.waylandSupport;
        }
        return new ClientInstance(clientLauncherProcess, profile, profileSettings);
    }

    private String getJavaDirName(Path javaPath) {
        String prefix = DirBridge.dirUpdates.toAbsolutePath().toString();
        if (javaPath == null || !javaPath.startsWith(prefix)) {
            return null;
        }
        Path result = DirBridge.dirUpdates.relativize(javaPath);
        return result.toString();
    }

    private void showJavaAlert(ClientProfile profile) {
        if ((JVMHelper.ARCH_TYPE == JVMHelper.ARCH.ARM32 || JVMHelper.ARCH_TYPE == JVMHelper.ARCH.ARM64)
                && profile.getVersion().compareTo(ClientProfileVersions.MINECRAFT_1_12_2) <= 0) {
            application.messageManager.showDialog(
                    application.getTranslation("runtime.scenes.serverinfo.javaalert.lwjgl2.header"),
                    application.getTranslation("runtime.scenes.serverinfo.javaalert.lwjgl2.description")
                               .formatted(profile.getRecommendJavaVersion()), () -> {}, () -> {}, true);
        } else {
            application.messageManager.showDialog(
                    application.getTranslation("runtime.scenes.serverinfo.javaalert.header"),
                    application.getTranslation("runtime.scenes.serverinfo.javaalert.description")
                               .formatted(profile.getRecommendJavaVersion()), () -> {}, () -> {}, true);
        }
    }

    public CompletableFuture<ClientInstance> launchClient() {
        return launchClient(application.getMainStage());
    }

    private CompletableFuture<ClientInstance> launchClient(AbstractStage stage) {
        ClientProfile profile = application.profilesService.getProfile();
        if (profile == null) throw new NullPointerException("profilesService.getProfile() is null");
        CompletableFuture<ClientInstance> future = new CompletableFuture<>();
        application.gui.processingOverlay.processRequest(stage, application.getTranslation("runtime.overlay.processing.text.setprofile"),
                                                         new SetProfileRequest(profile), (result) -> ContextHelper.runInFxThreadStatic(() -> {
                    RuntimeSettings.ProfileSettings profileSettings = application.getProfileSettings();
                    JavaHelper.JavaVersion javaVersion = null;
                    for (JavaHelper.JavaVersion v : application.javaService.javaVersions) {
                        if (v.jvmDir.toAbsolutePath().toString().equals(profileSettings.javaPath)) {
                            javaVersion = v;
                        }
                    }
                    if (javaVersion == null
                            && profileSettings.javaPath != null
                            && !application.guiModuleConfig.forceDownloadJava) {
                        try {
                            javaVersion = JavaHelper.JavaVersion.getByPath(Paths.get(profileSettings.javaPath));
                        } catch (Throwable e) {
                            if (LogHelper.isDevEnabled()) {
                                LogHelper.error(e);
                            }
                            LogHelper.warning("Incorrect java path %s", profileSettings.javaPath);
                        }
                    }
                    if (javaVersion == null || application.javaService.isIncompatibleJava(javaVersion, profile)) {
                        javaVersion = application.javaService.getRecommendJavaVersion(profile);
                    }
                    if (javaVersion == null) {
                        showJavaAlert(profile);
                        return;
                    }
                    String jvmDirName = getJavaDirName(javaVersion.jvmDir);
                    if (jvmDirName != null) {
                        final JavaHelper.JavaVersion finalJavaVersion = javaVersion;
                        try {
                            stage.setScene(application.gui.updateScene, true);
                            application.gui.updateScene.reset();
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                        application.gui.updateScene.
                                sendUpdateRequest(jvmDirName, javaVersion.jvmDir, null, true,
                                                  application.profilesService.getOptionalView(), false, isTestUpdate(profile, profileSettings),
                                                  (jvmHDir) -> {
                                                      if (JVMHelper.OS_TYPE == JVMHelper.OS.LINUX
                                                              || JVMHelper.OS_TYPE == JVMHelper.OS.MACOSX) {
                                                          Path javaFile = finalJavaVersion.jvmDir.resolve("bin")
                                                                                                 .resolve("java");
                                                          if (Files.exists(javaFile)) {
                                                              if (!javaFile.toFile().setExecutable(true)) {
                                                                  LogHelper.warning(
                                                                          "Set permission for %s unsuccessful",
                                                                          javaFile.toString());
                                                              }
                                                          }
                                                      }
                                                      downloadClients(future, profile, profileSettings, finalJavaVersion, jvmHDir);
                                                  });
                    } else {
                        try {
                            stage.setScene(application.gui.updateScene, true);
                            application.gui.updateScene.reset();
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                        downloadClients(future, profile, profileSettings, javaVersion, null);
                    }
                }), future::completeExceptionally, null);
        return future;
    }

    public class ClientInstance {
        private final ClientLauncherProcess process;
        private final ClientProfile clientProfile;
        private final RuntimeSettings.ProfileSettings settings;
        private final Thread writeParamsThread;
        private Thread runThread;
        private final CompletableFuture<Void> onWriteParams = new CompletableFuture<>();
        private final CompletableFuture<Integer> runFuture = new CompletableFuture<>();
        private final Set<ProcessListener> listeners = ConcurrentHashMap.newKeySet();

        public ClientInstance(ClientLauncherProcess process, ClientProfile clientProfile,
                RuntimeSettings.ProfileSettings settings) {
            this.process = process;
            this.clientProfile = clientProfile;
            this.settings = settings;
            this.writeParamsThread = CommonHelper.newThread("Client Params Writer Thread", true, () -> {
                try {
                    process.runWriteParams(
                            new InetSocketAddress("127.0.0.1", Launcher.getConfig().clientPort));
                    onWriteParams.complete(null);
                } catch (Throwable e) {
                    LogHelper.error(e);
                    onWriteParams.completeExceptionally(e);
                }
            });
        }

        private void run() {
            try {
                process.start(true);
                Process proc = process.getProcess();
                InputStream stream = proc.getInputStream();
                byte[] buf = IOHelper.newBuffer();
                try {
                    for (int length = stream.read(buf); length >= 0; length = stream.read(buf)) {
                        //append(new String(buf, 0, length));
                        handleListeners(buf, 0, length);
                    }
                } catch (EOFException ignored) {
                }
                if (proc.isAlive()) proc.waitFor();
                if(writeParamsThread != null && writeParamsThread.isAlive()) {
                    writeParamsThread.interrupt();
                }
                runFuture.complete(proc.exitValue());
            } catch (Throwable e) {
                if(writeParamsThread != null && writeParamsThread.isAlive()) {
                    writeParamsThread.interrupt();
                }
                runFuture.completeExceptionally(e);
            }
        }

        public void kill() {
            process.getProcess().destroyForcibly();
        }

        private void handleListeners(byte[] buf, int offset, int length) {
            for(var l : listeners) {
                l.onNext(buf, offset, length);
            }
        }

        public synchronized CompletableFuture<Integer> start() {
            if(runThread == null) {
                runThread = CommonHelper.newThread("Run Thread", true, this::run);
                writeParamsThread.start();
                runThread.start();
            }
            return runFuture;
        }

        public ClientLauncherProcess getProcess() {
            return process;
        }

        public ClientProfile getClientProfile() {
            return clientProfile;
        }

        public RuntimeSettings.ProfileSettings getSettings() {
            return settings;
        }

        public CompletableFuture<Void> getOnWriteParamsFuture() {
            return onWriteParams;
        }

        public void registerListener(ProcessListener listener) {
            listeners.add(listener);
        }

        public void unregisterListener(ProcessListener listener) {
            listeners.remove(listener);
        }

        @FunctionalInterface
        public interface ProcessListener {
            void onNext(byte[] buf, int offset, int length);
        }
    }
}
