package pro.gravit.launcher.client.gui.overlay;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.downloader.ListDownloader;
import pro.gravit.launcher.events.request.UpdateRequestEvent;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.hasher.HashedFile;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.update.UpdateRequest;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class UpdateOverlay extends AbstractOverlay {
    public ProgressBar progressBar;
    public Circle[] phases;
    public Label speed;
    public Text logOutput;

    public long totalSize;
    public UpdateOverlay(JavaFXApplication application) throws IOException {
        super("overlay/update/update.fxml", application);
    }

    @Override
    protected void doInit() throws IOException {
        progressBar = (ProgressBar) pane.lookup("#progress");
        phases = new Circle[5];
        for(int i=1;i<=5;++i)
        {
            phases[i-1] = (Circle) pane.lookup("#phase".concat(Integer.toString(i)));
        }
        speed = (Label) pane.lookup("#speed");
        logOutput = (Text) pane.lookup("#headingUpdate");
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

    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, ClientProfile profile, boolean optionalsEnabled, ContextHelper.GuiExceptionCallback onSuccess)
    {
        UpdateRequest request = new UpdateRequest(dirName, dir, matcher, digest);
        try {
            application.requestHandler.request(request).thenAccept(updateRequestEvent -> {
                LogHelper.dev("Start updating %s", dirName);
                if(optionalsEnabled)
                    profile.pushOptionalFile(updateRequestEvent.hdir, digest);
                try {
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Hashing %s", dirName)));
                    if(!IOHelper.exists(dir)) Files.createDirectories(dir);
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Create Diff %s", dirName)));
                    HashedDir.Diff diff = updateRequestEvent.hdir.diff(hashedDir, matcher);
                    final List<ListDownloader.DownloadTask> adds = new ArrayList<>();
                    diff.mismatch.walk(IOHelper.CROSS_SEPARATOR, (path, name, entry) -> {
                        switch (entry.getType())
                        {
                            case FILE:
                                HashedFile file = (HashedFile) entry;
                                totalSize += file.size;
                                adds.add(new ListDownloader.DownloadTask(path, file.size));
                                break;
                            case DIR:
                                Files.createDirectories(dir.resolve(path));
                                break;
                        }
                        return HashedDir.WalkAction.CONTINUE;
                    });
                    LogHelper.info("Diff %d %d", diff.mismatch.size(), diff.extra.size());
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Sorting files %s", dirName)));
                    adds.sort((Comparator.comparingLong((t) -> t.size)));

                    int threadNumber = 4;
                    CompletableFuture[] futures = new CompletableFuture[threadNumber];
                    for(int i=0;i<threadNumber;++i) futures[i] = new CompletableFuture();
                    final List<List<ListDownloader.DownloadTask>> tasks = new ArrayList<>(threadNumber);
                    for(int i=0;i<threadNumber;++i) tasks.add(new ArrayList<>());
                    int limitInOneTask = adds.size() / threadNumber;
                    int currentNumber = 0;
                    int currentProcessedTasks = 0;
                    for(ListDownloader.DownloadTask task : adds)
                    {
                        if(currentProcessedTasks == limitInOneTask && currentNumber != threadNumber-1) { currentNumber++; currentProcessedTasks = 0; }
                        tasks.get(currentNumber).add(task);
                        currentProcessedTasks++;
                    }
                    for(int i=0;i<threadNumber;++i)
                    {
                        int finalI = i;
                        futures[i] = CompletableFuture.runAsync(() -> {
                            List<ListDownloader.DownloadTask> myTasks = tasks.get(finalI);
                            for(ListDownloader.DownloadTask currentTask : myTasks)
                            {
                                try {
                                    URL u = new URL(updateRequestEvent.url + currentTask.apply);
                                    URLConnection connection = u.openConnection();
                                    try(InputStream input = connection.getInputStream())
                                    {
                                        Path filePath = dir.resolve(currentTask.apply);
                                        LogHelper.info("Thread %d download file %s", finalI, currentTask.apply);
                                        transfer(input, filePath, currentTask.apply, currentTask.size);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }, application.executors);
                    }
                    CompletableFuture.allOf(futures).thenAccept((e) -> {
                        ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Delete Extra files %s", dirName)));
                        try {
                            deleteExtraDir(dir, diff.extra, diff.extra.flag);
                            onSuccess.call();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).exceptionally((e) -> {
                        LogHelper.error(e);
                        return null;
                    });

                } catch (IOException e) {
                    e.printStackTrace();
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
    public static void transfer(InputStream input, Path file, String filename, long size) throws IOException {
        try (OutputStream fileOutput = IOHelper.newOutput(file)) {
            long downloaded = 0L;

            // Download with digest update
            byte[] bytes = IOHelper.newBuffer();
            while (downloaded < size) {
                int remaining = (int) Math.min(size - downloaded, bytes.length);
                int length = input.read(bytes, 0, remaining);
                if (length < 0)
                    throw new EOFException(String.format("%d bytes remaining", size - downloaded));

                // Update file
                fileOutput.write(bytes, 0, length);

                // Update state
                downloaded += length;
                //totalDownloaded += length;
            }
        }
    }
    public void addLog(String string)
    {
        LogHelper.dev("Update event %s", string);
        logOutput.setText(logOutput.getText().concat(string).concat("\n"));
    }

    @Override
    public void reset() {

    }

    @Override
    public void errorHandle(String error) {
        LogHelper.error(error);
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }
}
