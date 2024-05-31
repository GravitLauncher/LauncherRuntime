package pro.gravit.launcher.gui.scenes.update;

import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.core.hasher.FileNameMatcher;
import pro.gravit.launcher.core.hasher.HashedDir;
import pro.gravit.launcher.base.profiles.optional.OptionalView;
import pro.gravit.utils.helper.LogHelper;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public class UpdateScene extends AbstractScene {
    private ProgressBar progressBar;
    private Label speed;
    private Label volume;
    private TextArea logOutput;
    private Button cancel;
    private Label speedtext;
    private Label speederr;
    private Pane speedon;

    private VisualDownloader downloader;
    private volatile DownloadStatus downloadStatus = DownloadStatus.COMPLETE;

    public UpdateScene(JavaFXApplication application) {
        super("scenes/update/update.fxml", application);
    }

    @Override
    protected void doInit() {
        progressBar = LookupHelper.lookup(layout, "#progress");
        speed = LookupHelper.lookup(layout, "#speed");
        speederr = LookupHelper.lookup(layout, "#speedErr");
        speedon = LookupHelper.lookup(layout, "#speedOn");
        speedtext = LookupHelper.lookup(layout, "#speed-text");
        cancel = LookupHelper.lookup(layout, "#cancel");
        volume = LookupHelper.lookup(layout, "#volume");
        logOutput = LookupHelper.lookup(layout, "#outputUpdate");
        downloader = new VisualDownloader(application, progressBar, speed, volume, this::errorHandle,
                                          (log) -> contextHelper.runInFxThread(() -> addLog(log)), this::onUpdateStatus);
        LookupHelper.<ButtonBase>lookup(layout, "#cancel").setOnAction((e) -> {
            if (downloadStatus == DownloadStatus.DOWNLOAD && downloader.isDownload()) {
                downloader.cancel();
            } else if(downloadStatus == DownloadStatus.ERROR || downloadStatus == DownloadStatus.COMPLETE) {
                try {
                    switchToBackScene();
                } catch (Exception exception) {
                    errorHandle(exception);
                }
            }
        });
    }

    private void onUpdateStatus(DownloadStatus newStatus) {
        this.downloadStatus = newStatus;
        LogHelper.debug("Update download status: %s", newStatus.toString());
    }

    public void sendUpdateAssetRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest,
            String assetIndex, boolean test, Consumer<HashedDir> onSuccess) {
        downloader.sendUpdateAssetRequest(dirName, dir, matcher, digest, assetIndex, test, onSuccess);
    }

    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view,
            boolean optionalsEnabled, boolean test, Consumer<HashedDir> onSuccess) {
        downloader.sendUpdateRequest(dirName, dir, matcher, digest, view, optionalsEnabled, test, onSuccess);
    }

    public void addLog(String string) {
        LogHelper.dev("Update event %s", string);
        logOutput.appendText(string.concat("\n"));
    }

    @Override
    public void reset() {
        progressBar.progressProperty().setValue(0);
        logOutput.setText("");
        volume.setText("");
        speed.setText("0");
        progressBar.getStyleClass().removeAll("progress");
        speederr.setVisible(false);
        speedon.setVisible(true);
    }

    @Override
    public void errorHandle(Throwable e) {
        if(e instanceof CompletionException) {
            e = e.getCause();
        }
        addLog("Exception %s: %s".formatted(e.getClass(), e.getMessage() == null ? "" : e.getMessage()));
        progressBar.getStyleClass().add("progressError");
        speederr.setVisible(true);
        speedon.setVisible(false);
        LogHelper.error(e);
    }

    @Override
    public boolean isDisableReturnBack() {
        return true;
    }

    @Override
    public String getName() {
        return "update";
    }

    public enum DownloadStatus {
        ERROR, HASHING, REQUEST, DOWNLOAD, COMPLETE, DELETE
    }
}
