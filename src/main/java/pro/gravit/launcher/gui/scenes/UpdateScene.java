package pro.gravit.launcher.gui.scenes;

import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.basic.FxThreadExecutor;
import pro.gravit.launcher.gui.basic.ResourcePath;
import pro.gravit.launcher.gui.utils.OneTimeAction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ResourcePath("scenes/update/update.fxml")
public class UpdateScene extends FxScene {
    private ProgressBar progress;
    private TextArea outputUpdate;
    private final OneTimeAction updateProgressAction = new OneTimeAction();
    private final OneTimeAction updateOutputAction = new OneTimeAction();
    private Runnable cancelAction;
    @Override
    protected void doInit() {
        progress = lookup("#progress");
        outputUpdate = lookup("#outputUpdate");
        this.<Button>lookup("#cancel").setOnAction((e) -> {
            if(cancelAction != null) cancelAction.run();
        });
    }

    private void updateProgress(long current, long total) {
        progress.setProgress(((double) current / total));
    }

    private void updateOutput(String stage) {
        outputUpdate.appendText(stage+"\n");
    }

    public CompletableFuture<LauncherBackendAPI.ReadyProfile> startDownload(ProfileFeatureAPI.ClientProfile profile, LauncherBackendAPI.ClientProfileSettings settings) {
        return LauncherBackendAPIHolder.getApi().downloadProfile(profile, settings, makeCallback());
    }

    private LauncherBackendAPI.DownloadCallback makeCallback() {
        return new UpdateCallbackImpl();
    }

    private class UpdateCallbackImpl extends LauncherBackendAPI.DownloadCallback {
        private final AtomicLong totalDownload = new AtomicLong();
        private final AtomicLong currentDownloaded = new AtomicLong();
        @Override
        public void onStage(String stage) {
            updateOutputAction.run(() -> {
                updateOutput(stage);
            }, FxThreadExecutor.getInstance());
        }

        @Override
        public void onCanCancel(Runnable cancel) {
            CompletableFuture.runAsync(() -> {
                cancelAction = cancel;
            }, FxThreadExecutor.getInstance());
        }

        @Override
        public void onTotalDownload(long total) {
            fxUpdateProgress(total, currentDownloaded.get());
        }

        @Override
        public void onCurrentDownloaded(long current) {
            fxUpdateProgress(totalDownload.get(), current);
        }

        private void fxUpdateProgress(long current, long total) {
            updateProgressAction.run(() -> {
                updateProgress(current, total);
            }, FxThreadExecutor.getInstance());
        }
    }
}
