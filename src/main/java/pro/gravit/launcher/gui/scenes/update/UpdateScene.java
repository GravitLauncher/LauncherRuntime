package pro.gravit.launcher.gui.scenes.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;

import java.util.concurrent.CompletionException;

public class UpdateScene extends FxScene {

    private static final Logger logger =
            LoggerFactory.getLogger(UpdateScene.class);

    private ProgressBar progressBar;
    private Label speed;
    private Label volume;
    private TextArea logOutput;
    private Button cancel;
    private Label speedtext;
    private Label speederr;
    private Pane speedon;
    private final EventHandler<ActionEvent> cancelHandler = event -> onCancelAction();

    private VisualDownloader downloader;

    public UpdateScene(JavaFXApplication application) {
        super("scenes/update/update.fxml", application);
        this.isResetOnShow = true;
    }

    @Override
    protected void doInit() {
        progressBar = LookupHelper.lookup(layout, ProgressBar.class, "#progress");
        speed = LookupHelper.lookup(layout, Label.class, "#speed");
        speederr = LookupHelper.lookup(layout, Label.class, "#speedErr");
        speedon = LookupHelper.lookup(layout, Pane.class, "#speedOn");
        speedtext = LookupHelper.lookup(layout, Label.class, "#speed-text");
        cancel = LookupHelper.lookup(layout, Button.class, "#cancel");
        volume = LookupHelper.lookup(layout, Label.class, "#volume");
        logOutput = LookupHelper.lookup(layout, TextArea.class, "#outputUpdate");
        cancel.addEventHandler(ActionEvent.ACTION, cancelHandler);
        cancel.setDisable(true);
        scope.add(() -> {
            cancel.removeEventHandler(ActionEvent.ACTION, cancelHandler);
            unbindDownloader();
            if (downloader != null) downloader.invalidate();
        });
    }

    public VisualDownloader makeDownloadCallback() {
        FxDispatcher.checkThread();
        if (downloader != null) downloader.invalidate();
        unbindDownloader();
        downloader = new VisualDownloader(application, this::addLog);
        bindDownloader(downloader);
        return downloader;
    }

    private void bindDownloader(VisualDownloader session) {
        progressBar.progressProperty().bind(session.progressProperty());
        speed.textProperty().bind(session.speedProperty());
        volume.textProperty().bind(session.volumeProperty());
        cancel.disableProperty().bind(session.canCancelProperty().not().and(session.activeProperty()));
    }

    private void unbindDownloader() {
        if (progressBar != null) progressBar.progressProperty().unbind();
        if (speed != null) speed.textProperty().unbind();
        if (volume != null) volume.textProperty().unbind();
        if (cancel != null) cancel.disableProperty().unbind();
    }

    private void onCancelAction() {
        if (downloader != null && downloader.isActive()) {
            if (!downloader.canCancelProperty().get()) return;
            downloader.cancel();
        }
        try {
            switchToBackScene();
        } catch (Exception exception) {
            errorHandle(exception);
        }
    }

    public void addLog(String string) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            contextHelper.runInFxThread(() -> addLog(string));
            return;
        }
        logger.info("Update event {}", string);
        logOutput.appendText(string.concat("\n"));
    }

    @Override
    public void reset() {
        makeDownloadCallback();
        logOutput.setText("");
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
        if (downloader != null) downloader.finish();
        progressBar.getStyleClass().add("progressError");
        speederr.setVisible(true);
        speedon.setVisible(false);
        logger.error("", e);
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
