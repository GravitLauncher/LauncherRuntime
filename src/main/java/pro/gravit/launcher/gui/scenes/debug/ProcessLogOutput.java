package pro.gravit.launcher.gui.scenes.debug;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Thread-safe process monitor with bounded log storage and FX-observable state. */
public class ProcessLogOutput extends LauncherBackendAPI.RunCallback {
    static final int MAX_LENGTH = 1024 * 256;
    static final int MAX_LINES = 10_000;
    static final int MAX_DECODE_BYTES = 8192;
    private static final ScheduledExecutorService UPDATES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "process-log-updates");
        thread.setDaemon(true);
        return thread;
    });
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile(
            "^(?:\\d{4}-\\d{2}-\\d{2}[ T])?\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\s+");
    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^(?:Exception in thread \"[^\"]+\" )?[\\w$.]*(?:Exception|Error|Throwable)(?::.*)?$");
    private static final Pattern OMITTED_FRAMES = Pattern.compile(
            "^\\.\\.\\. \\d+ (?:more|common frames omitted)\\s*$");
    public enum Severity { NORMAL, WARN, ERROR, DEBUG }
    public record LogLine(String text, Severity severity) { }

    private final ObservableList<LogLine> lines = FXCollections.observableArrayList();
    private final ObservableList<LogLine> readOnlyLines = FXCollections.unmodifiableObservableList(lines);
    private final char[] buffer = new char[MAX_LENGTH];
    private int bufferStart;
    private int bufferLength;
    private long revision;
    private long clearGeneration;
    private final Consumer<Runnable> prepareUpdate;
    private final Consumer<Runnable> publishUpdate;
    private final Object bufferLock = new Object();
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
        this(exitWhenStarted, task -> UPDATES.schedule(task, 100, TimeUnit.MILLISECONDS), Platform::runLater);
    }

    ProcessLogOutput(boolean exitWhenStarted, Consumer<Runnable> prepareUpdate, Consumer<Runnable> publishUpdate) {
        this.exitWhenStarted = exitWhenStarted;
        this.prepareUpdate = prepareUpdate;
        this.publishUpdate = publishUpdate;
    }

    /** Observable log lines; access and observe on the FX thread. */
    public ObservableList<LogLine> getLines() { return readOnlyLines; }

    public ReadOnlyStringProperty textProperty() { return text.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty readyToExitProperty() { return ready.getReadOnlyProperty(); }

    public String getText() {
        String snapshot;
        synchronized (bufferLock) { snapshot = snapshotLocked(); }
        return retainedText(snapshot);
    }

    public void clear() {
        synchronized (bufferLock) {
            bufferStart = 0;
            bufferLength = 0;
            clearGeneration++;
            revision++;
        }
        queueTextUpdate();
    }

    public void append(String value) {
        append(value, false);
    }

    private void append(String value, boolean startNewLine) {
        if (value == null || value.isEmpty()) return;
        synchronized (bufferLock) {
            if (startNewLine && bufferLength > 0) {
                char last = buffer[(bufferStart + bufferLength - 1) % MAX_LENGTH];
                if (last != '\n' && last != '\r') appendLocked(System.lineSeparator());
            }
            appendLocked(value);
            revision++;
        }
        queueTextUpdate();
    }

    private void appendLocked(String value) {
        int offset = Math.max(0, value.length() - MAX_LENGTH);
        int length = value.length() - offset;
        int discarded = Math.max(0, bufferLength + length - MAX_LENGTH);
        bufferStart = (bufferStart + discarded) % MAX_LENGTH;
        bufferLength -= discarded;
        int end = (bufferStart + bufferLength) % MAX_LENGTH;
        int first = Math.min(length, MAX_LENGTH - end);
        value.getChars(offset, offset + first, buffer, end);
        value.getChars(offset + first, value.length(), buffer, 0);
        bufferLength += length;
        if (bufferLength > 0 && Character.isLowSurrogate(buffer[bufferStart])) {
            bufferStart = (bufferStart + 1) % MAX_LENGTH;
            bufferLength--;
        }
    }

    private String snapshotLocked() {
        char[] snapshot = new char[bufferLength];
        int first = Math.min(bufferLength, MAX_LENGTH - bufferStart);
        System.arraycopy(buffer, bufferStart, snapshot, 0, first);
        System.arraycopy(buffer, 0, snapshot, first, bufferLength - first);
        return new String(snapshot);
    }

    private static String retainedText(String value) {
        int count = value.isEmpty() || value.endsWith("\n") || value.endsWith("\r") ? 0 : 1;
        for (int i = value.length() - 1; i >= 0; i--) {
            char ch = value.charAt(i);
            if (ch != '\n' && ch != '\r') continue;
            int next = i + 1;
            if (ch == '\n' && i > 0 && value.charAt(i - 1) == '\r') i--;
            if (++count > MAX_LINES) return value.substring(next);
        }
        return value;
    }

    private void queueTextUpdate() {
        if (!textUpdateQueued.compareAndSet(false, true)) return;
        try {
            prepareUpdate.accept(this::prepareTextUpdate);
        } catch (RuntimeException failure) {
            textUpdateQueued.set(false);
            throw failure;
        }
    }

    private void prepareTextUpdate() {
        long shownRevision;
        long shownGeneration;
        String snapshot;
        synchronized (bufferLock) {
            snapshot = snapshotLocked();
            shownRevision = revision;
            shownGeneration = clearGeneration;
        }
        snapshot = retainedText(snapshot);
        List<LogLine> updated = parseRetainedLines(snapshot);
        String shownText = snapshot;
        try {
            publishUpdate.accept(() -> {
                try {
                    // A clear invalidates pending snapshots rather than briefly resurrecting old output.
                    boolean cleared;
                    synchronized (bufferLock) { cleared = clearGeneration != shownGeneration; }
                    if (!cleared) {
                        int unchanged = 0;
                        while (unchanged < lines.size() && unchanged < updated.size()
                                && lines.get(unchanged).equals(updated.get(unchanged))) unchanged++;
                        if (unchanged < lines.size()) lines.subList(unchanged, lines.size()).clear();
                        if (unchanged < updated.size()) lines.addAll(updated.subList(unchanged, updated.size()));
                        text.set(shownText);
                    }
                } finally {
                    textUpdateQueued.set(false);
                    boolean changed;
                    synchronized (bufferLock) { changed = revision != shownRevision; }
                    // Production scheduling is delayed and never recurses on the FX thread.
                    if (changed) queueTextUpdate();
                }
            });
        } catch (RuntimeException failure) {
            textUpdateQueued.set(false);
            throw failure;
        }
    }

    static List<LogLine> parseLines(String value) {
        return parseRetainedLines(retainedText(value));
    }

    private static List<LogLine> parseRetainedLines(String value) {
        List<LogLine> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\n' && ch != '\r') continue;
            result.add(logLine(value.substring(start, i)));
            if (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') i++;
            start = i + 1;
        }
        if (start < value.length()) result.add(logLine(value.substring(start)));
        return result;
    }

    private static LogLine logLine(String value) {
        Matcher timestamp = TIMESTAMP_PREFIX.matcher(value);
        int offset = timestamp.find() ? timestamp.end() : 0;
        Severity severity = Severity.NORMAL;
        boolean levelFound = false;
        while (offset < value.length() && value.charAt(offset) == '[') {
            int end = value.indexOf(']', offset + 1);
            if (end < 0) break;
            String field = value.substring(offset + 1, end);
            String level = field.substring(field.lastIndexOf('/') + 1);
            if (!levelFound && isLevel(level)) {
                severity = severity(level);
                levelFound = true;
            }
            offset = end + 1;
            while (offset < value.length() && Character.isWhitespace(value.charAt(offset))) offset++;
        }
        if (!levelFound) {
            int end = offset;
            while (end < value.length() && Character.isUpperCase(value.charAt(end))) end++;
            if (end == value.length() || Character.isWhitespace(value.charAt(end)) || value.charAt(end) == ':') {
                String level = value.substring(offset, end);
                if (isLevel(level)) {
                    severity = severity(level);
                    offset = end;
                }
            }
        }
        while (offset < value.length() && Character.isWhitespace(value.charAt(offset))) offset++;
        if (offset < value.length() && (value.charAt(offset) == ':' || value.charAt(offset) == '-')) offset++;
        String message = value.substring(offset).stripLeading();
        if (EXCEPTION_LINE.matcher(message).matches() || isStackFrame(message)) {
            severity = Severity.ERROR;
        }
        return new LogLine(value, severity);
    }

    private static boolean isStackFrame(String message) {
        if (message.startsWith("Caused by:") || message.startsWith("Suppressed:")) return true;
        if (message.startsWith("at ")) {
            int source = message.indexOf('(', 3);
            return source > 3 && !Character.isWhitespace(message.charAt(source - 1))
                    && message.indexOf(')', source + 1) >= 0;
        }
        return message.startsWith("...") && OMITTED_FRAMES.matcher(message).matches();
    }

    private static boolean isLevel(String value) {
        return switch (value) {
            case "WARN", "ERROR", "DEBUG", "INFO", "TRACE", "FATAL" -> true;
            default -> false;
        };
    }

    private static Severity severity(String field) {
        return switch (field) {
            case "WARN" -> Severity.WARN;
            case "ERROR" -> Severity.ERROR;
            case "DEBUG" -> Severity.DEBUG;
            default -> Severity.NORMAL;
        };
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
        append("Process finished with exit code " + code + System.lineSeparator(), true);
        updateRunning(false);
    }

    @Override
    public void onNormalOutput(byte[] buf, int offset, int size) {
        super.onNormalOutput(buf, offset, size);
        appendDecoded(stdoutDecoder, buf, offset, size);
    }

    @Override
    public void onErrorOutput(byte[] buf, int offset, int size) {
        super.onErrorOutput(buf, offset, size);
        appendDecoded(stderrDecoder, buf, offset, size);
    }

    private void appendDecoded(StreamDecoder decoder, byte[] bytes, int offset, int size) {
        Objects.checkFromIndexSize(offset, size, bytes.length);
        for (int end = offset + size; offset < end;) {
            int length = Math.min(MAX_DECODE_BYTES, end - offset);
            append(decoder.decode(bytes, offset, length));
            offset += length;
        }
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
        private final ByteBuffer pending = ByteBuffer.allocate(8);
        private ByteBuffer input = ByteBuffer.allocate(MAX_DECODE_BYTES + 8);
        private CharBuffer chars = CharBuffer.allocate(MAX_DECODE_BYTES + 8);
        private boolean finished;

        synchronized String decode(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (finished) throw new IllegalStateException("Decoder has already been finished");
            int required = pending.position() + length;
            if (input.capacity() < required) input = ByteBuffer.allocate(required);
            input.clear();
            pending.flip();
            input.put(pending).put(bytes, offset, length).flip();
            int requiredChars = Math.max(16, (int) (input.remaining() * decoder.maxCharsPerByte()) + 2);
            if (chars.capacity() < requiredChars) chars = CharBuffer.allocate(requiredChars);
            chars.clear();
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
