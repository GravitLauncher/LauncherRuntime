package pro.gravit.launcher.client.gui.scenes.update;

import javafx.scene.control.*;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.utils.helper.LogHelper;

import java.nio.file.Path;
import java.util.function.Consumer;

public class UpdateScene extends AbstractScene {
    private ProgressBar progressBar;
    private Label speed;
    private Label volume;
    private TextArea logOutput;
    private Button cancel;
    private Label speedtext;
    private Label speederr;

    private VisualDownloader downloader;

    public UpdateScene(JavaFXApplication application) {
        super("scenes/update/update.fxml", application);
    }

    @Override
    protected void doInit() {
        progressBar = LookupHelper.lookup(layout, "#progress");
        speed = LookupHelper.lookup(layout, "#speed");
        speederr = LookupHelper.lookup(layout, "#speedErr");
        speedtext = LookupHelper.lookup(layout, "#speed-text");
        cancel = LookupHelper.lookup(layout, "#cancel");
        volume = LookupHelper.lookup(layout, "#volume");
        logOutput = LookupHelper.lookup(layout, "#outputUpdate");
        logOutput.setText("");
        downloader = new VisualDownloader(application, progressBar, speed, volume, this::errorHandle,
                                          (log) -> contextHelper.runInFxThread(() -> addLog(log)));
        LookupHelper.<ButtonBase>lookup(layout, "#cancel").setOnAction((e) -> {
            if (downloader.isDownload()) {
                downloader.cancel();
            } else {
                try {
                    switchScene(application.gui.serverInfoScene);
                } catch (Exception exception) {
                    errorHandle(exception);
                }
            }
        });
    }

    public void sendUpdateAssetRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest,
            String assetIndex, Consumer<HashedDir> onSuccess) {
        downloader.sendUpdateAssetRequest(dirName, dir, matcher, digest, assetIndex, onSuccess);
    }

    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view,
            boolean optionalsEnabled, Consumer<HashedDir> onSuccess) {
        downloader.sendUpdateRequest(dirName, dir, matcher, digest, view, optionalsEnabled, onSuccess);
    }

    public void addLog(String string) {
        LogHelper.dev("Update event %s", string);
        logOutput.appendText(string.concat("\n"));
    }

    @Override
    public void reset() {
        progressBar.progressProperty().setValue(0);
        logOutput.clear();
        volume.setText("");
        speed.setText("0");
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
        addLog("Exception %s: %s".formatted(e.getClass(), e.getMessage() == null ? "" : e.getMessage()));
        progressBar.getStyleClass().add("progressError");
        speed.setStyle("-fx-opacity: 0");
        speedtext.setStyle("-fx-opacity: 0");
        speederr.setStyle("-fx-opacity: 1");
        LogHelper.error(e);
        cancel.setDisable(true);
        cancel.setStyle("-fx-opacity: 0");
    }

    @Override
    public String getName() {
        return "update";
    }
}
