package pro.gravit.launcher.client.gui.scenes.update;

import javafx.beans.property.DoubleProperty;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import pro.gravit.launcher.AsyncDownloader;
import pro.gravit.launcher.client.ClientLauncherProcess;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.hasher.HashedFile;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.profiles.optional.actions.OptionalAction;
import pro.gravit.launcher.profiles.optional.actions.OptionalActionFile;
import pro.gravit.launcher.request.update.UpdateRequest;
import pro.gravit.utils.Downloader;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class UpdateScene extends AbstractScene {
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private final AtomicLong lastDownloaded = new AtomicLong(0);
    private ProgressBar progressBar;
    private Text speed;
    private Label volume;
    private TextArea logOutput;
    private Text currentStatus;
    //private Button reload;
    //private Button cancel;
    private Text speedtext;
    private Text speederr;
    private long totalSize;
    private Downloader downloader;

    public UpdateScene(JavaFXApplication application) {
        super("scenes/update/update.fxml", application);
    }

    @Override
    protected void doInit() {
        progressBar = LookupHelper.lookup(layout, "#progress");
        speed = LookupHelper.lookup(layout, "#speed");
        speederr = LookupHelper.lookup(layout, "#speedErr");
        speedtext = LookupHelper.lookup(layout, "#speed-text");
        //cancel = LookupHelper.lookup(header, "#controls", "#controls", "#cancel");
        volume = LookupHelper.lookup(layout, "#volume");
        logOutput = LookupHelper.lookup(layout, "#outputUpdate");
        currentStatus = LookupHelper.lookup(layout, "#headingUpdate");
        logOutput.setText("");
        enableControlButton("#reload").ifPresent(e -> {
            e.setOnAction(x -> reset());
        });
        enableControlButton("#cancel").ifPresent(e -> e.setOnAction(
                (x) -> {
                    if (downloader != null) {
                        downloader.cancel();
                        downloader = null;
                    } else {
                        try {
                            switchScene(application.gui.serverInfoScene);
                        } catch (Exception exception) {
                            errorHandle(exception);
                        }
                    }
                }));
    }

    private void deleteExtraDir(Path subDir, HashedDir subHDir, boolean deleteDir) throws IOException {
        for (Map.Entry<String, HashedEntry> mapEntry : subHDir.map().entrySet()) {
            String name = mapEntry.getKey();
            Path path = subDir.resolve(name);

            // Delete list and dirs based on type
            HashedEntry entry = mapEntry.getValue();
            HashedEntry.Type entryType = entry.getType();
            switch (entryType) {
                case FILE:
                    Files.delete(path);
                    break;
                case DIR:
                    deleteExtraDir(path, (HashedDir) entry, deleteDir || entry.flag);
                    break;
                default:
                    throw new AssertionError("Unsupported hashed entry type: " + entryType.name());
            }
        }

        // Delete!
        if (deleteDir) {
            Files.delete(subDir);
        }
    }

    private static class PathRemapperData {
        public String key;
        public String value;

        public PathRemapperData(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class HashedClientResult {
        public HashedDir clientDir;
        public HashedDir assetsDir;
        public HashedDir javaDir;
        public List<ClientLauncherProcess.ClientParams.ClientZoneInfo> zones;

        public HashedClientResult(HashedDir clientDir) {
            this.clientDir = clientDir;
            this.zones = new ArrayList<>(1);
        }
    }

    public CompletableFuture<HashedClientResult> startClientDownload(ClientProfile profile, Path baseDir, Path clientDir, Path assetsDir, OptionalView view, Path javaDir, String javaDirName) {
        CompletableFuture<HashedDir> future = downloadStdDir(profile.getDir(), clientDir, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), view, true, null);
        CompletableFuture<HashedClientResult> result = future.thenCompose(e -> {
            HashedClientResult r = new HashedClientResult(e);
            return downloadStdDir(profile.getAssetDir(), assetsDir, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), null, false, null).thenApply((assetHDir) -> {
                r.assetsDir = assetHDir;
                return r;
            });
        });
        if(javaDir != null && javaDirName != null) {
            result = result.thenCompose((r) -> downloadStdDir(javaDirName, javaDir, null, profile.isUpdateFastCheck(), null, true, null).thenApply((en) -> {
                r.javaDir = en;
                return r;
            }));
        }
        Map<String, List<Path>> zoneInfo = collectZoneInfo(profile);
        for(Map.Entry<String, List<Path>> e : zoneInfo.entrySet()) {
            Path zonePath = baseDir.resolve(e.getKey());
            Function<HashedDir, HashedDir> modifyHashDir = (hdir) -> {
                return hdir.filter((p) -> {
                    if(!p.getFileName().toString().endsWith(".jar")) {
                        return true;
                    }
                    return e.getValue().contains(p);
                });
            };
            result = result.thenCompose((r)-> downloadStdDir(e.getKey(), zonePath, null, profile.isUpdateFastCheck(), null, false, modifyHashDir).thenApply((en) -> {
                r.zones.add(new ClientLauncherProcess.ClientParams.ClientZoneInfo(e.getKey(), zonePath.toString(), en));
                return r;
            }));
        }
        return result;
    }

    private Map<String, List<Path>> collectZoneInfo(ClientProfile profile) {
        Map<String, List<Path>> result = new HashMap<>();
        for(ClientProfile.ClientProfileLibrary library : profile.getLibraries()) {
            if(library.zone == null || library.zone.isEmpty() || library.zone.equals("@")) {
                continue;
            }
            List<Path> list = result.computeIfAbsent(library.zone, k -> new ArrayList<>(8));
            list.add(Paths.get(library.path));
        }
        return result;
    }

    private CompletableFuture<HashedDir> downloadStdDir(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view, boolean deleteExtra, Function<HashedDir, HashedDir> modifyHashedDir) {
        CompletableFuture<HashedDir> result = new CompletableFuture<>();
        if(application.offlineService.isOfflineMode()) {
            ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Hashing %s", dirName)));
            application.workers.submit(() -> {
                try {
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    result.complete(hashedDir);
                } catch (IOException e) {
                    errorHandle(e);
                    result.completeExceptionally(e);
                }
            });
            return result;
        }
        UpdateRequest request = new UpdateRequest(dirName);
        try {
            application.service.request(request).thenAccept(updateRequestEvent -> {
                LogHelper.dev("Start updating %s", dirName);
                totalDownloaded.set(0);
                lastUpdateTime.set(System.currentTimeMillis());
                lastDownloaded.set(0);
                totalSize = 0;
                progressBar.progressProperty().setValue(0);
                HashedDir remoteHashedDir;
                if(modifyHashedDir != null) {
                    remoteHashedDir = modifyHashedDir.apply(updateRequestEvent.hdir);
                } else {
                    remoteHashedDir = updateRequestEvent.hdir;
                }
                if (view != null) {
                    for (OptionalAction action : view.getDisabledActions()) {
                        if (action instanceof OptionalActionFile) {
                            ((OptionalActionFile) action).disableInHashedDir(remoteHashedDir);
                        }
                    }
                }
                try {
                    LinkedList<PathRemapperData> pathRemapper = new LinkedList<>();
                    if (view != null) {
                        Set<OptionalActionFile> fileActions = view.getActionsByClass(OptionalActionFile.class);
                        for (OptionalActionFile file : fileActions) {
                            file.injectToHashedDir(remoteHashedDir);
                            file.files.forEach((k, v) -> {
                                if (v == null || v.isEmpty()) return;
                                pathRemapper.add(new PathRemapperData(v, k)); //reverse (!)
                                LogHelper.dev("Remap prepare %s to %s", v, k);
                            });
                        }
                    }
                    pathRemapper.sort(Comparator.comparingInt(c -> -c.key.length())); // Support deep remap
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Hashing %s", dirName)));
                    if (!IOHelper.exists(dir))
                        Files.createDirectories(dir);
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    HashedDir.Diff diff = remoteHashedDir.diff(hashedDir, matcher);
                    final List<AsyncDownloader.SizedFile> adds = new ArrayList<>();
                    diff.mismatch.walk(IOHelper.CROSS_SEPARATOR, (path, name, entry) -> {
                        String urlPath = path;
                        switch (entry.getType()) {
                            case FILE:
                                HashedFile file = (HashedFile) entry;
                                totalSize += file.size;
                                for (PathRemapperData remapEntry : pathRemapper) {
                                    if (path.startsWith(remapEntry.key)) {
                                        urlPath = path.replace(remapEntry.key, remapEntry.value);
                                        LogHelper.dev("Remap found: injected url path: %s | calculated original url path: %s", path, urlPath);
                                    }
                                }
                                Files.deleteIfExists(dir.resolve(path));
                                adds.add(new AsyncDownloader.SizedFile(urlPath, path, file.size));
                                break;
                            case DIR:
                                Files.createDirectories(dir.resolve(path));
                                break;
                        }
                        return HashedDir.WalkAction.CONTINUE;
                    });
                    LogHelper.info("Diff %d %d", diff.mismatch.size(), diff.extra.size());
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Downloading %s...", dirName)));
                    ExecutorService executor = Executors.newWorkStealingPool(4);
                    downloader = Downloader.downloadList(adds, updateRequestEvent.url, dir, new Downloader.DownloadCallback() {
                        @Override
                        public void apply(long fullDiff) {
                            {
                                long old = totalDownloaded.getAndAdd(fullDiff);
                                updateProgress(old, old + fullDiff);
                            }
                        }

                        @Override
                        public void onComplete(Path path) {

                        }
                    }, executor, 4);
                    downloader.getFuture().thenAccept((e) -> {
                        if(deleteExtra) {
                            ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Delete Extra files %s", dirName)));
                            try {
                                deleteExtraDir(dir, diff.extra, diff.extra.flag);
                                result.complete(remoteHashedDir);
                            } catch (Exception ex) {
                                result.completeExceptionally(ex);
                                errorHandle(ex);
                            }
                        } else {
                            result.complete(remoteHashedDir);
                        }
                    }).exceptionally((e) -> {
                        result.completeExceptionally(e);
                        ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
                        return null;
                    }).thenAccept((e) -> {
                        executor.shutdown();
                    });
                } catch (Exception e) {
                    result.completeExceptionally(e);
                    ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
                }
            }).exceptionally((error) -> {
                result.completeExceptionally(error);
                ContextHelper.runInFxThreadStatic(() -> errorHandle(error.getCause()));
                // hide(2500, scene, onError);
                return null;
            });
        } catch (IOException e) {
            result.completeExceptionally(e);
            ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
        }
        return result;
    }

    private void addLog(String string) {
        LogHelper.dev("Update event %s", string);
        logOutput.appendText(string.concat("\n"));
    }

    private void updateProgress(long oldValue, long newValue) {
        double add = (double) (newValue - oldValue) / (double) totalSize; // 0.0 - 1.0
        DoubleProperty property = progressBar.progressProperty();
        property.set(property.get() + add);
        long lastTime = lastUpdateTime.get();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= 130) {
            String format = String.format(" [%.1f MB]", (double) newValue / (1024.0 * 1024.0), (double) totalSize / (1024.0 * 1024.0));
            double bytesSpeed = (double) (newValue - lastDownloaded.get()) / (double) (currentTime - lastTime) * 1000.0;
            String speedFormat = String.format("%.2f ", bytesSpeed * 8 / (1000.0 * 1000.0));
            ContextHelper.runInFxThreadStatic(() -> {
                volume.setText(format);
                speed.setText(speedFormat);
            });
            lastUpdateTime.set(currentTime);
            lastDownloaded.set(newValue);
        }

    }

    @Override
    public void reset() {
        progressBar.progressProperty().setValue(0);
        logOutput.clear();
        volume.setText("");
        speed.setText("0");
        //reload.setDisable(true);
        //reload.setStyle("-fx-opacity: 0");
        //cancel.setDisable(false);
        //cancel.setStyle("-fx-opacity: 1");
        progressBar.getStyleClass().removeAll("progress");
        speed.getStyleClass().removeAll("speedError");
        speed.setStyle("-fx-opacity: 1");
        speedtext.setStyle("-fx-opacity: 1");
        speederr.setStyle("-fx-opacity: 0");
    }

    @Override
    public void errorHandle(Throwable e) {
        addLog(String.format("Exception %s: %s", e.getClass(), e.getMessage() == null ? "" : e.getMessage()));
        progressBar.getStyleClass().add("progressError");
        speed.setStyle("-fx-opacity: 0");
        speedtext.setStyle("-fx-opacity: 0");
        speederr.setStyle("-fx-opacity: 1");
        LogHelper.error(e);
        //reload.setDisable(false);
        //reload.setStyle("-fx-opacity: 1");
        //cancel.setDisable(true);
        //cancel.setStyle("-fx-opacity: 0");
    }

    @Override
    public String getName() {
        return "update";
    }
}
