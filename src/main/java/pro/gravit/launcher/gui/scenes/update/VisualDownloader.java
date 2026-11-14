package pro.gravit.launcher.gui.scenes.update;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Download progress state independent of the JavaFX nodes that display it. */
public class VisualDownloader extends LauncherBackendAPI.DownloadCallback {
    private final JavaFXApplication application;
    private final Consumer<String> addLog;
    private final Object stateLock = new Object();
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(-1);
    private final ReadOnlyStringWrapper speed = new ReadOnlyStringWrapper("0");
    private final ReadOnlyStringWrapper volume = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper canCancel = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper cancelled = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper active = new ReadOnlyBooleanWrapper(true);

    private Runnable cancelCallback;
    private boolean cancelRequested;
    private boolean cancellationDelivered;
    private volatile boolean valid = true;
    private volatile boolean userCancelled;
    private long generation;
    private long downloaded;
    private long total = -1;
    private long lastUpdateTime = System.currentTimeMillis();
    private long lastDownloaded;

    public VisualDownloader(JavaFXApplication application, Consumer<String> addLog) {
        this.application = application;
        this.addLog = addLog;
    }

    public ReadOnlyDoubleProperty progressProperty() { return progress.getReadOnlyProperty(); }
    public ReadOnlyStringProperty speedProperty() { return speed.getReadOnlyProperty(); }
    public ReadOnlyStringProperty volumeProperty() { return volume.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty canCancelProperty() { return canCancel.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty cancelledProperty() { return cancelled.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty activeProperty() { return active.getReadOnlyProperty(); }

    @Override
    public void onStartPhase(UpdatePhase phase) {
        super.onStartPhase(phase);
        logTranslation("runtime.scenes.update.phase." + phase.name().toLowerCase());
    }

    @Override
    public void onStage(String stage) {
        super.onStage(stage);
        synchronized (stateLock) {
            if (!valid) return;
            generation++;
            downloaded = 0;
            total = -1;
            lastDownloaded = 0;
            lastUpdateTime = System.currentTimeMillis();
        }
        logTranslation("runtime.scenes.update.stage." + stage);
        queueSnapshot();
        if (UpdateScene.DownloadStatus.COMPLETE.name().equalsIgnoreCase(stage)
                || UpdateScene.DownloadStatus.ERROR.name().equalsIgnoreCase(stage)) finish();
    }

    @Override
    public void onCanCancel(Runnable cancel) {
        super.onCanCancel(cancel);
        boolean invokeImmediately;
        synchronized (stateLock) {
            invokeImmediately = cancel != null && cancelRequested && !cancellationDelivered;
            if (invokeImmediately) cancellationDelivered = true;
            if (!invokeImmediately && valid) cancelCallback = cancel;
        }
        if (invokeImmediately) {
            runCancel(cancel);
        } else {
            FxDispatcher.INSTANCE.execute(() -> {
                if (valid && !userCancelled) canCancel.set(cancel != null);
            });
        }
    }

    @Override
    public void onTotalDownload(long total) {
        super.onTotalDownload(total);
        synchronized (stateLock) {
            if (!valid) return;
            this.total = total > 0 ? total : -1;
        }
        queueSnapshot();
    }

    @Override
    public void onCurrentDownloaded(long current) {
        super.onCurrentDownloaded(current);
        synchronized (stateLock) {
            if (!valid) return;
            downloaded = Math.max(0, downloaded + current);
        }
        queueSnapshot();
    }

    private void logTranslation(String key) {
        FxDispatcher.INSTANCE.execute(() -> {
            if (valid && !userCancelled) addLog.accept(application.getTranslation(key));
        });
    }

    private void queueSnapshot() {
        if (updateQueued.compareAndSet(false, true)) FxDispatcher.INSTANCE.execute(this::applySnapshot);
    }

    private void applySnapshot() {
        updateQueued.set(false);
        if (!valid || userCancelled) return;

        long value;
        long size;
        long priorValue;
        long priorTime;
        long snapshotGeneration;
        long now = System.currentTimeMillis();
        synchronized (stateLock) {
            value = downloaded;
            size = total;
            priorValue = lastDownloaded;
            priorTime = lastUpdateTime;
            snapshotGeneration = generation;
            if (now - lastUpdateTime >= 130) {
                lastDownloaded = value;
                lastUpdateTime = now;
            }
        }
        if (!valid || userCancelled) return;
        progress.set(size <= 0 ? -1 : Math.min(1.0, (double) value / size));
        volume.set(size <= 0
                ? "%.1f MB / ?".formatted((double) value / (1024.0 * 1024.0))
                : "%.1f/%.1f MB".formatted((double) value / (1024.0 * 1024.0),
                        (double) size / (1024.0 * 1024.0)));
        if (now - priorTime >= 130) {
            long elapsed = Math.max(1, now - priorTime);
            double bitsPerSecond = (double) (value - priorValue) * 8 / elapsed * 1000.0;
            speed.set("%.1f".formatted(bitsPerSecond / (1000.0 * 1000.0)));
        }
        synchronized (stateLock) {
            if (snapshotGeneration != generation) queueSnapshot();
        }
    }

    public void cancel() {
        Runnable callback;
        synchronized (stateLock) {
            if (cancelRequested || !valid) return;
            cancelRequested = true;
            userCancelled = true;
            valid = false;
            callback = cancelCallback;
            cancellationDelivered = callback != null;
            cancelCallback = null;
        }
        FxDispatcher.INSTANCE.execute(() -> {
            cancelled.set(true);
            canCancel.set(false);
            active.set(false);
        });
        if (callback != null) runCancel(callback);
    }

    private static void runCancel(Runnable callback) {
        try { callback.run(); }
        catch (Throwable ignored) { /* Backend cancellation is best-effort. */ }
    }

    public boolean isCancelled() { return userCancelled; }
    public boolean isActive() { return valid; }

    /** Marks the operation complete without representing a user cancellation. */
    public void finish() {
        synchronized (stateLock) {
            if (!valid) return;
            valid = false;
            cancelCallback = null;
        }
        FxDispatcher.INSTANCE.execute(() -> {
            active.set(false);
            canCancel.set(false);
        });
    }

    /** Invalidates this session so queued and future callbacks cannot alter its view state. */
    public void invalidate() {
        finish();
    }
}
