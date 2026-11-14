package pro.gravit.launcher.gui.scenes.debug;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe process monitor with bounded log storage and FX-observable state. */
public class ProcessLogOutput extends LauncherBackendAPI.RunCallback {
    static final int MAX_LENGTH = 1024 * 256;
    private final StringBuilder buffer = new StringBuilder();
    private final Object bufferLock = new Object();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean textUpdateQueued = new AtomicBoolean();
    private final AtomicBoolean isRunning = new AtomicBoolean();
    private final AtomicBoolean readyToExit = new AtomicBoolean();
    private final ReadOnlyStringWrapper text = new ReadOnlyStringWrapper(this, "text", "");
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(this, "running", false);
    private final ReadOnlyBooleanWrapper ready = new ReadOnlyBooleanWrapper(this, "readyToExit", false);
    private final StreamDecoder stdoutDecoder = new StreamDecoder();
    private final StreamDecoder stderrDecoder = new StreamDecoder();
    private final boolean exitWhenStarted;
    private volatile Runnable terminateProcessCallback;

    public ProcessLogOutput(boolean exitWhenStarted) {
        this.exitWhenStarted = exitWhenStarted;
    }

    public ReadOnlyStringProperty textProperty() { return text.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty readyToExitProperty() { return ready.getReadOnlyProperty(); }

    public String getText() {
        synchronized (bufferLock) { return buffer.toString(); }
    }

    public void clear() {
        synchronized (bufferLock) {
            buffer.setLength(0);
            revision.incrementAndGet();
        }
        queueTextUpdate();
    }

    public void append(String value) {
        if (value == null || value.isEmpty()) return;
        synchronized (bufferLock) {
            if (value.length() >= MAX_LENGTH) {
                buffer.setLength(0);
                buffer.append(value, value.length() - MAX_LENGTH, value.length());
            } else {
                int excess = buffer.length() + value.length() - MAX_LENGTH;
                if (excess > 0) buffer.delete(0, excess);
                buffer.append(value);
            }
            revision.incrementAndGet();
        }
        queueTextUpdate();
    }

    private void queueTextUpdate() {
        if (!textUpdateQueued.compareAndSet(false, true)) return;
        FxDispatcher.INSTANCE.run(() -> {
            long shownRevision;
            String snapshot;
            synchronized (bufferLock) {
                snapshot = buffer.toString();
                shownRevision = revision.get();
            }
            text.set(snapshot);
            textUpdateQueued.set(false);
            if (revision.get() != shownRevision) queueTextUpdate();
        });
    }

    private void updateRunning(boolean value) {
        isRunning.set(value);
        FxDispatcher.INSTANCE.run(() -> running.set(value));
    }

    public boolean isRunning() { return isRunning.get(); }
    public boolean isReadyToExit() { return readyToExit.get(); }

    /** Called before ReadyProfile.run so navigation/reload cannot race process startup. */
    public void markStarting() {
        readyToExit.set(false);
        FxDispatcher.INSTANCE.run(() -> ready.set(false));
        updateRunning(true);
    }
    public void markLaunchFailed() { updateRunning(false); }

    public void terminate() {
        Runnable callback = terminateProcessCallback;
        if (callback != null) callback.run();
    }

    /** Kept for source compatibility; hiding the view must not detach the process monitor. */
    public void detach() { }

    @Override
    public void onStarted() {
        super.onStarted();
        updateRunning(true);
    }

    @Override
    public void onCanTerminate(Runnable terminate) {
        super.onCanTerminate(terminate);
        terminateProcessCallback = terminate;
    }

    @Override
    public void onFinished(int code) {
        super.onFinished(code);
        append(stdoutDecoder.finish());
        append(stderrDecoder.finish());
        append("Process finished with exit code " + code + System.lineSeparator());
        updateRunning(false);
    }

    @Override
    public void onNormalOutput(byte[] buf, int offset, int size) {
        super.onNormalOutput(buf, offset, size);
        append(stdoutDecoder.decode(buf, offset, size));
    }

    @Override
    public void onErrorOutput(byte[] buf, int offset, int size) {
        super.onErrorOutput(buf, offset, size);
        append(stderrDecoder.decode(buf, offset, size));
    }

    @Override
    public void onReadyToExit() {
        super.onReadyToExit();
        readyToExit.set(true);
        FxDispatcher.INSTANCE.run(() -> {
            ready.set(true);
            if (exitWhenStarted) {
                JavaFXApplication application = JavaFXApplication.getInstance();
                if (application != null && application.getMainStage() != null) application.getMainStage().close();
            }
        });
    }

    static final class StreamDecoder {
        private final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        private ByteBuffer pending = ByteBuffer.allocate(8);
        private boolean finished;

        synchronized String decode(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (finished) throw new IllegalStateException("Decoder has already been finished");
            ByteBuffer input = ByteBuffer.allocate(pending.position() + length);
            pending.flip();
            input.put(pending).put(bytes, offset, length).flip();
            CharBuffer chars = CharBuffer.allocate(Math.max(16, (int) (input.remaining() * decoder.maxCharsPerByte()) + 2));
            CoderResult result = decoder.decode(input, chars, false);
            if (result.isError()) throw new IllegalStateException("Unable to decode process output", codingFailure(result));
            pending.clear();
            if (input.hasRemaining()) pending.put(input);
            chars.flip();
            return chars.toString();
        }

        synchronized String finish() {
            if (finished) return "";
            finished = true;
            pending.flip();
            CharBuffer chars = CharBuffer.allocate(16);
            CoderResult result = decoder.decode(pending, chars, true);
            if (result.isError()) throw new IllegalStateException("Unable to finish decoding process output", codingFailure(result));
            result = decoder.flush(chars);
            if (result.isError()) throw new IllegalStateException("Unable to flush process output decoder", codingFailure(result));
            chars.flip();
            return chars.toString();
        }

        private static Exception codingFailure(CoderResult result) {
            try { result.throwException(); return new IllegalStateException("Unexpected decoder result"); }
            catch (Exception error) { return error; }
        }
    }
}
