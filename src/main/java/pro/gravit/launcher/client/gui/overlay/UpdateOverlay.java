package pro.gravit.launcher.client.gui.overlay;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import pro.gravit.launcher.AsyncDownloader;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.FXMLConsumer;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.hasher.HashedFile;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.update.UpdateRequest;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class UpdateOverlay extends AbstractOverlay implements FXMLConsumer {
    public ProgressBar progressBar;
    public Circle[] phases;
    public Label speed;
    public Label volume;
    public TextArea logOutput;
    public Text currentStatus;

    public long totalSize;
    public AtomicLong totalDownloaded = new AtomicLong(0);
    private AtomicLong lastUpdateTime = new AtomicLong(0);
    private AtomicLong lastDownloaded = new AtomicLong(0);
    public int currentPhase = 0;
    public double phaseOffset;
    public double progressRatio = 1.0;
    public double phaseRatio;
    public UpdateOverlay(JavaFXApplication application) throws IOException {
        super("overlay/update/update.fxml", application);
    }

    @Override
    protected void doInit() throws IOException {
        progressBar = (ProgressBar) pane.lookup("#progress");
        phases = new Circle[5];
        for(int i=1;i<=5;++i)
        {
            Circle c = (Circle) pane.lookup("#phase".concat(Integer.toString(i)));
            //c.getStyleClass().add("phaseactive");
            phases[i-1] = c;
            phaseOffset = (c.getRadius()*2.0)/progressBar.getPrefWidth();
            progressRatio-=phaseOffset;
        }
        phaseRatio = progressRatio / 4.0;
        speed = (Label) pane.lookup("#speed");
        volume = (Label) pane.lookup("#volume");
        logOutput = (TextArea) pane.lookup("#outputUpdate");
        currentStatus = (Text) pane.lookup("#headingUpdate");
        logOutput.setText("");
        ((ButtonBase)pane.lookup("#close") ).setOnAction((e) -> {
            //TODO
            Platform.exit();
        });
        ((ButtonBase)pane.lookup("#hide") ).setOnAction((e) -> {
            //TODO
            //.setIconified(true);
        });
    }

    private void deleteExtraDir(Path subDir, HashedDir subHDir, boolean flag) throws IOException {
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
                    deleteExtraDir(path, (HashedDir) entry, flag || entry.flag);
                    break;
                default:
                    throw new AssertionError("Unsupported hashed entry type: " + entryType.name());
            }
        }

        // Delete!
        if (flag) {
            Files.delete(subDir);
        }
    }

    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, ClientProfile profile, boolean optionalsEnabled, Consumer<HashedDir> onSuccess)
    {
        UpdateRequest request = new UpdateRequest(dirName);
        try {
            application.service.request(request).thenAccept(updateRequestEvent -> {
                LogHelper.dev("Start updating %s", dirName);
                totalDownloaded.set(0);
                lastUpdateTime.set(System.currentTimeMillis());
                lastDownloaded.set(0);
                totalSize = 0;
                if(optionalsEnabled)
                    profile.pushOptionalFile(updateRequestEvent.hdir, digest);
                try {
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Hashing %s", dirName)));
                    if(!IOHelper.exists(dir)) Files.createDirectories(dir);
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    HashedDir.Diff diff = updateRequestEvent.hdir.diff(hashedDir, matcher);
                    final List<AsyncDownloader.SizedFile> adds = new ArrayList<>();
                    diff.mismatch.walk(IOHelper.CROSS_SEPARATOR, (path, name, entry) -> {
                        switch (entry.getType())
                        {
                            case FILE:
                                HashedFile file = (HashedFile) entry;
                                totalSize += file.size;
                                adds.add(new AsyncDownloader.SizedFile(path, file.size));
                                break;
                            case DIR:
                                Files.createDirectories(dir.resolve(path));
                                break;
                        }
                        return HashedDir.WalkAction.CONTINUE;
                    });
                    LogHelper.info("Diff %d %d", diff.mismatch.size(), diff.extra.size());
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Downloading %s...", dirName)));
                    AsyncDownloader asyncDownloader = new AsyncDownloader((d) -> {
                        long old = totalDownloaded.getAndAdd(d);
                        updateProgress(old, old+d);
                    });
                    List<List<AsyncDownloader.SizedFile>> tasks = asyncDownloader.sortFiles(adds, 4);
                    CompletableFuture[] futures = asyncDownloader.runDownloadList(tasks, updateRequestEvent.url, dir, application.workers);
                    CompletableFuture.allOf(futures).thenAccept((e) -> {
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

                } catch (IOException e) {
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
    public void addLog(String string)
    {
        LogHelper.dev("Update event %s", string);
        logOutput.appendText(string.concat("\n"));
    }
    public void initNewPhase(String name)
    {
        currentStatus.setText(name);
        phases[currentPhase].getStyleClass().add("phaseActive");
        DoubleProperty property = progressBar.progressProperty();
        property.set(phaseOffset+(phaseOffset+phaseRatio)*currentPhase);
        LogHelper.debug("NewPhase %f", progressBar.progressProperty().doubleValue());
        currentPhase++;
    }
    public void updateProgress(long oldValue, long newValue)
    {
        double add = (double)(newValue - oldValue)/(double)totalSize; // 0.0 - 1.0
        DoubleProperty property = progressBar.progressProperty();
        property.set(property.get()+add*phaseRatio);
        long rawLastTime = lastUpdateTime.get();
        long rawThisTime = System.currentTimeMillis();
        if(rawThisTime - rawLastTime >= 130)
        {
            String format = String.format("%.1f MB / %.1f MB", (double)newValue / (1024.0*1024.0), (double)totalSize / (1024.0*1024.0));
            double bytesSpeed = (double)(newValue - lastDownloaded.get()) / (double)(rawThisTime-rawLastTime) * 1000.0;
            String speedFormat = String.format("%.2f MiB/s", bytesSpeed*8 / (1000.0*1000.0));
            ContextHelper.runInFxThreadStatic(() -> { volume.setText(format); speed.setText(speedFormat); });
            lastUpdateTime.set(rawThisTime);
            lastDownloaded.set(newValue);
        }

    }
    @Override
    public void reset() {

    }

    @Override
    public void errorHandle(String error) {
        addLog(String.format("Error: %s" ,error));
        LogHelper.error(error);
    }

    @Override
    public void errorHandle(Throwable e) {
        addLog(String.format("Exception %s: %s", e.getClass(), e.getMessage() == null ? "" : e.getMessage()));
        phases[currentPhase].getStyleClass().add("phaseError");
        LogHelper.error(e);
    }

    @Override
    public String getFxmlPath() {
        return fxmlPath;
    }
}
