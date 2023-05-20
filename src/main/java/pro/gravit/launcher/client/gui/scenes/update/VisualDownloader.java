package pro.gravit.launcher.client.gui.scenes.update;

import javafx.beans.property.DoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import pro.gravit.launcher.AsyncDownloader;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.events.request.UpdateRequestEvent;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.hasher.HashedFile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.profiles.optional.actions.OptionalAction;
import pro.gravit.launcher.profiles.optional.actions.OptionalActionFile;
import pro.gravit.launcher.request.RequestService;
import pro.gravit.launcher.request.update.UpdateRequest;
import pro.gravit.utils.Downloader;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class VisualDownloader {
    private final JavaFXApplication application;
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private final AtomicLong lastDownloaded = new AtomicLong(0);

    private volatile long totalSize;
    private volatile Downloader downloader;

    private final ProgressBar progressBar;
    private final Text speed;
    private final Label volume;

    private final Consumer<Throwable> errorHandle;
    private final Consumer<String> addLog;

    private final ExecutorService executor;

    public VisualDownloader(JavaFXApplication application, ProgressBar progressBar, Text speed, Label volume, Consumer<Throwable> errorHandle, Consumer<String> addLog) {
        this.application = application;
        this.progressBar = progressBar;
        this.speed = speed;
        this.volume = volume;
        this.errorHandle = errorHandle;
        this.addLog = addLog;
        this.executor = new ForkJoinPool(application.guiModuleConfig.downloadThreads, (pool) -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setDaemon(true);
            return thread;
        }, null, true);
    }

    public void sendUpdateAssetRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, String assetIndex, Consumer<HashedDir> onSuccess) {
        if (application.offlineService.isOfflineMode()) {
            addLog.accept(String.format("Hashing %s", dirName));
            application.workers.submit(() -> {
                try {
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    onSuccess.accept(hashedDir);
                } catch (IOException e) {
                    errorHandle.accept(e);
                }
            });
            return;
        }
        UpdateRequest request = new UpdateRequest(dirName);
        try {
            application.service.request(request).thenAccept(event -> {
                LogHelper.dev("Start updating %s", dirName);
                try {
                    downloadAsset(dirName, dir, matcher, digest, assetIndex, onSuccess, event.hdir, event.url);
                } catch (Exception e) {
                    ContextHelper.runInFxThreadStatic(() -> errorHandle.accept(e));
                }
            }).exceptionally((error) -> {
                ContextHelper.runInFxThreadStatic(() -> errorHandle.accept(error.getCause()));
                // hide(2500, scene, onError);
                return null;
            });
        } catch (IOException e) {
            errorHandle.accept(e);
        }
    }

    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view, boolean optionalsEnabled, Consumer<HashedDir> onSuccess) {
        if (application.offlineService.isOfflineMode()) {
            addLog.accept(String.format("Hashing %s", dirName));
            application.workers.submit(() -> {
                try {
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    onSuccess.accept(hashedDir);
                } catch (IOException e) {
                    errorHandle.accept(e);
                }
            });
            return;
        }
        UpdateRequest request = new UpdateRequest(dirName);
        try {
            application.service.request(request).thenAccept(event -> {
                LogHelper.dev("Start updating %s", dirName);
                try {
                    download(dirName, dir, matcher, digest, view, optionalsEnabled, onSuccess, event.hdir, event.url);
                } catch (Exception e) {
                    ContextHelper.runInFxThreadStatic(() -> errorHandle.accept(e));
                }
            }).exceptionally((error) -> {
                ContextHelper.runInFxThreadStatic(() -> errorHandle.accept(error.getCause()));
                // hide(2500, scene, onError);
                return null;
            });
        } catch (IOException e) {
            errorHandle.accept(e);
        }
    }

    private void download(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view, boolean optionalsEnabled, Consumer<HashedDir> onSuccess, HashedDir targetHDir, String baseUrl) throws Exception {
        LinkedList<PathRemapperData> pathRemapper = optionalsEnabled ? getPathRemapper(view, targetHDir) : new LinkedList<>();
        addLog.accept(String.format("Hashing %s", dirName));
        if (!IOHelper.exists(dir))
            Files.createDirectories(dir);

        HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
        HashedDir.Diff diff = targetHDir.diff(hashedDir, matcher);
        final List<AsyncDownloader.SizedFile> adds = getFilesList(dir, pathRemapper, diff.mismatch);

        LogHelper.info("Diff %d %d", diff.mismatch.size(), diff.extra.size());
        addLog.accept(String.format("Downloading %s...", dirName));
        downloadFiles(dir, adds, baseUrl, () -> {
            try {
                addLog.accept(String.format("Delete Extra files %s", dirName));
                deleteExtraDir(dir, diff.extra, diff.extra.flag);
                onSuccess.accept(targetHDir);
            } catch (IOException e) {
                errorHandle.accept(e);
            }
        });
    }

    private void downloadAsset(String dirName, Path dir, FileNameMatcher matcher, boolean digest, String assetIndex, Consumer<HashedDir> onSuccess, HashedDir targetHDir, String baseUrl) throws Exception {
        LinkedList<PathRemapperData> pathRemapper = new LinkedList<>();
        addLog.accept(String.format("Check assetIndex %s", assetIndex));
        if (!IOHelper.exists(dir))
            Files.createDirectories(dir);
        Consumer<HashedDir> downloadAssetRunnable = (assetHDir) -> {
            try {
                HashedDir hashedDir = new HashedDir(dir, matcher, false, digest);
                HashedDir.Diff diff = assetHDir.diff(hashedDir, matcher);
                final List<AsyncDownloader.SizedFile> adds = getFilesList(dir, pathRemapper, diff.mismatch);

                LogHelper.info("Diff %d %d", diff.mismatch.size(), diff.extra.size());
                addLog.accept(String.format("Downloading %s...", dirName));
                downloadFiles(dir, adds, baseUrl, () -> {
                    try {
                        onSuccess.accept(assetHDir);
                    } catch (Exception e) {
                        errorHandle.accept(e);
                    }
                });
            } catch (Throwable e) {
                errorHandle.accept(e);
            }
        };
        {
            String assetIndexPath = "indexes/".concat(assetIndex).concat(".json");
            Path localAssetIndexPath = dir.resolve(assetIndexPath);
            boolean needUpdateIndex;
            HashedDir.FindRecursiveResult result = targetHDir.findRecursive(assetIndexPath);
            if(!(result.entry instanceof HashedFile)) {
                addLog.accept(String.format("ERROR: assetIndex %s not found", assetIndex));
                errorHandle.accept(new RuntimeException("assetIndex not found"));
                return;
            };
            if(Files.exists(localAssetIndexPath)) {
                HashedFile file = new HashedFile(localAssetIndexPath, Files.size(localAssetIndexPath), true);
                needUpdateIndex = !((HashedFile)result.entry).isSame(file);
            } else {
                IOHelper.createParentDirs(localAssetIndexPath);
                needUpdateIndex = true;
            }
            if(needUpdateIndex) {
                List<AsyncDownloader.SizedFile> adds = new ArrayList<>(1);
                adds.add(new AsyncDownloader.SizedFile(assetIndexPath, ((HashedFile) result.entry).size));
                downloadFiles(dir, adds, baseUrl, () -> {
                    try {
                        AssetIndexHelper.AssetIndex index = AssetIndexHelper.parse(localAssetIndexPath);
                        AssetIndexHelper.modifyHashedDir(index, targetHDir);
                        downloadAssetRunnable.accept(targetHDir);
                    } catch (Exception e) {
                        errorHandle.accept(e);
                    }
                });
            } else {
                try {
                    AssetIndexHelper.AssetIndex index = AssetIndexHelper.parse(localAssetIndexPath);
                    AssetIndexHelper.modifyHashedDir(index, targetHDir);
                    downloadAssetRunnable.accept(targetHDir);
                } catch (Exception e) {
                    errorHandle.accept(e);
                }
            }
        }
    }

    private void downloadFiles(Path dir, List<AsyncDownloader.SizedFile> adds, String baseUrl, Runnable onSuccess) throws Exception {
        ContextHelper.runInFxThreadStatic(this::resetProgress).thenAccept((x) -> {
            downloader = Downloader.downloadList(adds, baseUrl, dir, new Downloader.DownloadCallback() {
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
            }, executor, application.guiModuleConfig.downloadThreads);
            downloader.getFuture()
                    .thenAccept((e) -> onSuccess.run())
                    .exceptionally((e) -> {
                        ContextHelper.runInFxThreadStatic(() -> errorHandle.accept(e));
                        return null;
                    });
        });
    }

    private void resetProgress() {
        totalDownloaded.set(0);
        lastUpdateTime.set(System.currentTimeMillis());
        lastDownloaded.set(0);
        progressBar.progressProperty().setValue(0);
    }

    private List<AsyncDownloader.SizedFile> getFilesList(Path dir, LinkedList<PathRemapperData> pathRemapper, HashedDir mismatch) throws IOException {
        totalSize = 0;

        final List<AsyncDownloader.SizedFile> adds = new ArrayList<>();
        mismatch.walk(IOHelper.CROSS_SEPARATOR, (path, name, entry) -> {
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
        return adds;
    }

    private LinkedList<PathRemapperData> getPathRemapper(OptionalView view, HashedDir hdir) {
        for (OptionalAction action : view.getDisabledActions()) {
            if (action instanceof OptionalActionFile) {
                ((OptionalActionFile) action).disableInHashedDir(hdir);
            }
        }
        LinkedList<PathRemapperData> pathRemapper = new LinkedList<>();
        Set<OptionalActionFile> fileActions = view.getActionsByClass(OptionalActionFile.class);
        for (OptionalActionFile file : fileActions) {
            file.injectToHashedDir(hdir);
            file.files.forEach((k, v) -> {
                if (v == null || v.isEmpty()) return;
                pathRemapper.add(new PathRemapperData(v, k)); //reverse (!)
                LogHelper.dev("Remap prepare %s to %s", v, k);
            });
        }
        pathRemapper.sort(Comparator.comparingInt(c -> -c.key.length())); // Support deep remap
        return pathRemapper;
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

    public boolean isDownload() {
        return downloader != null && !downloader.getFuture().isDone();
    }

    public CompletableFuture<Void> getFuture() {
        return downloader.getFuture();
    }

    public void cancel() {
        downloader.cancel();
    }

    public boolean isCanceled() {
        return downloader.isCanceled();
    }
}
