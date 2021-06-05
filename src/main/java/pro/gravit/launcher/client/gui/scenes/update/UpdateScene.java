package pro.gravit.launcher.client.gui.scenes.update;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import pro.gravit.launcher.AsyncDownloader;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.hasher.HashedFile;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class UpdateScene extends AbstractScene {
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private final AtomicLong lastDownloaded = new AtomicLong(0);
    private ProgressBar progressBar;
    private Text speed;
    private Label volume;
    private TextArea logOutput;
    private Text currentStatus;
    private Button reload;
    private Button cancel;
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
        reload = LookupHelper.lookup(layout, "#reload");
        cancel = LookupHelper.lookup(layout, "#cancel");
        volume = LookupHelper.lookup(layout, "#volume");
        logOutput = LookupHelper.lookup(layout, "#outputUpdate");
        currentStatus = LookupHelper.lookup(layout, "#headingUpdate");
        logOutput.setText("");
        LookupHelper.<ButtonBase>lookup(layout, "#reload").setOnAction(
                (e) -> reset()
        );
        LookupHelper.<ButtonBase>lookup(layout, "#cancel").setOnAction(
                (e) -> {
                    if(downloader != null) {
                        downloader.cancel();
                        downloader = null;
                    } else {
                        try {
                            switchScene(application.gui.serverInfoScene);
                        } catch (Exception exception) {
                            errorHandle(exception);
                        }
                    }
                });
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
    @SuppressWarnings("rawtypes")
    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view, boolean optionalsEnabled, Consumer<HashedDir> onSuccess) {
        UpdateRequest request = new UpdateRequest(dirName);
        try {
            application.service.request(request).thenAccept(updateRequestEvent -> {
                LogHelper.dev("Start updating %s", dirName);
                totalDownloaded.set(0);
                lastUpdateTime.set(System.currentTimeMillis());
                lastDownloaded.set(0);
                totalSize = 0;
                if (optionalsEnabled) {
                    for(OptionalAction action : view.getDisabledActions())
                    {
                        if(action instanceof OptionalActionFile)
                        {
                            ((OptionalActionFile) action).disableInHashedDir(updateRequestEvent.hdir);
                        }
                    }
                }
                try {
                    LinkedList<PathRemapperData> pathRemapper = new LinkedList<>();
                    if(optionalsEnabled)
                    {
                        Set<OptionalActionFile> fileActions = view.getActionsByClass(OptionalActionFile.class);
                        for(OptionalActionFile file : fileActions)
                        {
                            file.injectToHashedDir(updateRequestEvent.hdir);
                            file.files.forEach((k,v) -> {
                                if(v == null || v.isEmpty()) return;
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
                    HashedDir.Diff diff = updateRequestEvent.hdir.diff(hashedDir, matcher);
                    final List<AsyncDownloader.SizedFile> adds = new ArrayList<>();
                    diff.mismatch.walk(IOHelper.CROSS_SEPARATOR, (path, name, entry) -> {
                        String urlPath = path;
                        switch (entry.getType()) {
                            case FILE:
                                HashedFile file = (HashedFile) entry;
                                totalSize += file.size;
                                for(PathRemapperData remapEntry : pathRemapper)
                                {
                                    if(path.startsWith(remapEntry.key))
                                    {
                                        urlPath = path.replace(remapEntry.key, remapEntry.value);
                                        LogHelper.dev("Remap found: injected url path: %s | calculated original url path: %s", path, urlPath);
                                    }
                                }
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
                    Downloader downloader = Downloader.downloadList(adds, updateRequestEvent.url, dir, new Downloader.DownloadCallback() {
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
                    }, application.workers, 4);
                    downloader.getFuture().thenAccept((e) -> {
                        ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Delete Extra files %s", dirName)));
                        try {
                            deleteExtraDir(dir, diff.extra, diff.extra.flag);
                            onSuccess.accept(updateRequestEvent.hdir);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).exceptionally((e) -> {
                        ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
                        return null;
                    });

                } catch (Exception e) {
                    ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
                }
            }).exceptionally((error) -> {
                ContextHelper.runInFxThreadStatic(() -> errorHandle(error.getCause()));
                // hide(2500, scene, onError);
                return null;
            });
        } catch (IOException e) {
            errorHandle(e);
        }
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
       reload.setDisable(true);
        reload.setStyle("-fx-opacity: 0");
        cancel.setDisable(false);
        cancel.setStyle("-fx-opacity: 1");
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
        reload.setDisable(false);
        reload.setStyle("-fx-opacity: 1");
        cancel.setDisable(true);
        cancel.setStyle("-fx-opacity: 0");
    }

    @Override
    public String getName() {
        return "update";
    }
}
