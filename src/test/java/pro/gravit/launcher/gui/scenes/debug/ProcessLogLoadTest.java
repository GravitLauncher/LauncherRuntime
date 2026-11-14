package pro.gravit.launcher.gui.scenes.debug;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProcessLogLoadTest {
    @Test
    void overloadKeepsOnlyOnePreparedOrPublishedUpdateAndNewestOutput() throws Exception {
        ArrayDeque<Runnable> prepare = new ArrayDeque<>();
        ArrayDeque<Runnable> publish = new ArrayDeque<>();
        ProcessLogOutput monitor = new ProcessLogOutput(false, prepare::add, publish::add);
        String chunk = "[ERROR] load test message\n".repeat(100);
        for (int i = 0; i < 10_000; i++) monitor.append(chunk);
        assertEquals(1, prepare.size());
        assertTrue(monitor.getText().length() <= ProcessLogOutput.MAX_LENGTH);
        prepare.remove().run();
        assertEquals(1, publish.size());
        for (int i = 0; i < 10_000; i++) monitor.append(chunk);
        monitor.append("[WARN] newest message\n");
        assertTrue(prepare.isEmpty(), "A stalled FX thread must not accumulate background snapshots");
        assertEquals(1, publish.size());
        FxTestThread.run(publish.remove());
        assertEquals(1, prepare.size());
        prepare.remove().run();
        FxTestThread.run(publish.remove());
        FxTestThread.run(() -> {
            assertEquals(monitor.getText(), monitor.textProperty().get());
            assertTrue(monitor.getLines().size() <= ProcessLogOutput.MAX_LINES);
            assertEquals("[WARN] newest message", monitor.getLines().getLast().text());
        });
        assertTrue(prepare.isEmpty());
        assertTrue(publish.isEmpty());
    }

    @Test
    void clearInvalidatesAPreparedSnapshotEvenIfNewOutputArrives() throws Exception {
        ArrayDeque<Runnable> prepare = new ArrayDeque<>();
        ArrayDeque<Runnable> publish = new ArrayDeque<>();
        ProcessLogOutput monitor = new ProcessLogOutput(false, prepare::add, publish::add);
        monitor.append("old process\n");
        prepare.remove().run();
        monitor.clear();
        monitor.append("new process\n");
        FxTestThread.run(publish.remove());
        FxTestThread.run(() -> assertTrue(monitor.getLines().isEmpty()));
        prepare.remove().run();
        FxTestThread.run(publish.remove());
        FxTestThread.run(() -> assertEquals("new process\n", monitor.textProperty().get()));
    }

    @Test
    void ringRetainsTheSameTailAsAStringAcrossWrapsAndHugeWrites() {
        ProcessLogOutput monitor = new ProcessLogOutput(false, task -> { }, task -> { });
        String expected = "";
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < 200; i++) {
            String chunk = Integer.toHexString(i).repeat(random.nextInt(150_000) + 1);
            monitor.append(chunk);
            expected += chunk;
            if (expected.length() > ProcessLogOutput.MAX_LENGTH) {
                expected = expected.substring(expected.length() - ProcessLogOutput.MAX_LENGTH);
            }
            assertEquals(expected, monitor.getText());
        }
    }

    @Test
    void hugeByteCallbacksPreserveSplitUtf8AndBoundRetainedRows() throws Exception {
        ProcessLogOutput monitor = new ProcessLogOutput(false, Runnable::run, Runnable::run);
        byte[] bytes = "[DEBUG] €😀 details\r\n".repeat(20_000).getBytes(StandardCharsets.UTF_8);
        FxTestThread.run(() -> {
            monitor.onNormalOutput(bytes, 0, bytes.length);
            assertTrue(monitor.getText().length() <= ProcessLogOutput.MAX_LENGTH);
            assertEquals(ProcessLogOutput.MAX_LINES, monitor.getLines().size());
            assertFalse(monitor.getText().contains("�"));
            assertEquals("[DEBUG] €😀 details", monitor.getLines().getLast().text());
        });
    }

    @Test
    void productionSchedulerYieldsToFxTasksDuringContinuousLogging() throws Exception {
        FxTestThread.start();
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        AtomicInteger pulses = new AtomicInteger();
        Stage stage = FxTestThread.call(() -> {
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            DebugScene.configureOutput(output);
            output.setItems(monitor.getLines());
            Scene scene = new Scene(new StackPane(output), 400, 300);
            scene.addPostLayoutPulseListener(pulses::incrementAndGet);
            Stage window = new Stage();
            window.setScene(scene);
            window.show();
            return window;
        });
        AtomicBoolean producing = new AtomicBoolean(true);
        Thread producer = new Thread(() -> {
            long sequence = 0;
            while (producing.get()) {
                monitor.append(("[DEBUG] background output " + sequence++ + "\n").repeat(100));
            }
        }, "test-log-producer");
        producer.setDaemon(true);
        producer.start();
        try {
            CompletableFuture<Void> heartbeat = new CompletableFuture<>();
            Platform.runLater(() -> heartbeat.complete(null));
            heartbeat.get(5, TimeUnit.SECONDS);
            for (int i = 0; i < 10; i++) {
                Thread.sleep(100);
                FxTestThread.run(() -> assertTrue(monitor.getLines().size() <= ProcessLogOutput.MAX_LINES));
            }
        } finally {
            producing.set(false);
            producer.join(5_000);
        }
        try {
            assertFalse(producer.isAlive());
            monitor.append("[ERROR] final marker\n");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!FxTestThread.call(() -> monitor.getText().equals(monitor.textProperty().get()))) {
                assertTrue(System.nanoTime() < deadline, "The final tail must reach the UI after the producer stops");
                Thread.sleep(20);
            }
            FxTestThread.run(() -> assertEquals("[ERROR] final marker", monitor.getLines().getLast().text()));
            assertTrue(pulses.get() > 1, "Real UI pulses must continue while log output is produced");
        } finally {
            FxTestThread.run(() -> {
                stage.setScene(null);
                stage.close();
            });
        }
    }

    @Test
    void malformedLongStackFrameDoesNotCauseRegexBacktracking() {
        String malformed = "at " + "method(".repeat(30_000);
        assertEquals(ProcessLogOutput.Severity.NORMAL,
                ProcessLogOutput.parseLines(malformed).getFirst().severity());
    }
}
