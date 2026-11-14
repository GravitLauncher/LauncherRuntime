package pro.gravit.launcher.gui.scenes.debug;

import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProcessLogOutputTest {
    @Test
    void decoderPreservesUtf8CharactersSplitAcrossChunks() {
        ProcessLogOutput.StreamDecoder decoder = new ProcessLogOutput.StreamDecoder();
        byte[] bytes = "A€B".getBytes(StandardCharsets.UTF_8);

        assertEquals("A", decoder.decode(bytes, 0, 2));
        assertEquals("€B", decoder.decode(bytes, 2, bytes.length - 2));
        assertEquals("", decoder.finish());
    }

    @Test
    void finishEmitsReplacementForIncompleteFinalCodePoint() {
        ProcessLogOutput.StreamDecoder decoder = new ProcessLogOutput.StreamDecoder();
        byte[] incomplete = {(byte) 0xE2, (byte) 0x82};

        assertEquals("", decoder.decode(incomplete, 0, incomplete.length));
        assertEquals("�", decoder.finish());
    }

    @Test
    void bufferRetainsAtMostTheMostRecentMaximumLength() throws Exception {
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> monitor.append("a".repeat(ProcessLogOutput.MAX_LENGTH + 25)));

        assertEquals(ProcessLogOutput.MAX_LENGTH, monitor.getText().length());
        assertTrue(monitor.getText().chars().allMatch(value -> value == 'a'));
    }

    @Test
    void recognizesLevelsOnlyInLogPrefixes() {
        assertEquals(ProcessLogOutput.Severity.WARN, line("[12:30:45] [Render thread/WARN]: warning").severity());
        assertEquals(ProcessLogOutput.Severity.ERROR, line("2026-11-14 12:30:45.123 [main] ERROR: failed").severity());
        assertEquals(ProcessLogOutput.Severity.DEBUG, line("[DEBUG] details").severity());
        assertEquals(ProcessLogOutput.Severity.WARN, line("WARN warning").severity());
        assertEquals(ProcessLogOutput.Severity.NORMAL, line("Message mentions ERROR and [WARN]").severity());
        assertEquals(ProcessLogOutput.Severity.NORMAL, line("[main/INFO] ERROR is mentioned in a message").severity());
        assertEquals(ProcessLogOutput.Severity.NORMAL, line("WARNING is a different token").severity());
        assertEquals(ProcessLogOutput.Severity.ERROR, line("    at some.Stack.frame(Stack.java:1)").severity());
    }

    @Test
    void handlesLineEndingsAndFinalPartialLine() {
        var lines = ProcessLogOutput.parseLines("[WARN] first\r\n\n[ERROR] last");
        assertEquals(3, lines.size());
        assertEquals("[WARN] first", lines.get(0).text());
        assertEquals("", lines.get(1).text());
        assertEquals(ProcessLogOutput.Severity.ERROR, lines.get(2).severity());
        assertTrue(ProcessLogOutput.parseLines("").isEmpty());
        assertEquals(1, ProcessLogOutput.parseLines("line\n").size());
    }

    @Test
    void reclassifiesSplitTagsAndKeepsPlainText() throws Exception {
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> {
            monitor.append("[WA");
            assertEquals(ProcessLogOutput.Severity.NORMAL, monitor.getLines().getFirst().severity());
            monitor.append("RN] warning\r");
            monitor.append("\n[DEBUG] details");
            assertEquals(ProcessLogOutput.Severity.WARN, monitor.getLines().getFirst().severity());
            assertEquals(2, monitor.getLines().size());
            assertEquals(ProcessLogOutput.Severity.DEBUG, monitor.getLines().getLast().severity());
            assertEquals("[WARN] warning\r\n[DEBUG] details", monitor.getText());
            assertEquals(monitor.getText(), monitor.textProperty().get());
            monitor.clear();
            assertTrue(monitor.getLines().isEmpty());
            assertEquals("", monitor.getText());
        });
    }

    @Test
    void treatsBothStreamsEquallyAndFlushesFinalLine() throws Exception {
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> {
            byte[] warning = "[WARN] warning\n".getBytes(StandardCharsets.UTF_8);
            byte[] debug = "[DEBUG] details\n".getBytes(StandardCharsets.UTF_8);
            byte[] neutral = "untagged stderr\n".getBytes(StandardCharsets.UTF_8);
            monitor.onNormalOutput(warning, 0, warning.length);
            monitor.onErrorOutput(debug, 0, debug.length);
            monitor.onErrorOutput(neutral, 0, neutral.length);
            assertEquals(ProcessLogOutput.Severity.WARN, monitor.getLines().get(0).severity());
            assertEquals(ProcessLogOutput.Severity.DEBUG, monitor.getLines().get(1).severity());
            assertEquals(ProcessLogOutput.Severity.NORMAL, monitor.getLines().get(2).severity());
            monitor.append("[ERROR] final");
            monitor.onFinished(0);
            assertEquals(ProcessLogOutput.Severity.ERROR, monitor.getLines().get(3).severity());
            assertEquals("[ERROR] final", monitor.getLines().get(3).text());
            assertEquals("Process finished with exit code 0", monitor.getLines().get(4).text());
        });
    }

    @Test
    void trimmingKeepsStyledLinesConsistentWithRetainedText() throws Exception {
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> {
            monitor.append("[ERROR] old\n" + "x".repeat(ProcessLogOutput.MAX_LENGTH));
            monitor.append("\n[WARN] retained");
            assertEquals(ProcessLogOutput.MAX_LENGTH, monitor.getText().length());
            assertEquals(ProcessLogOutput.parseLines(monitor.getText()), monitor.getLines());
            assertEquals(ProcessLogOutput.Severity.NORMAL, monitor.getLines().getFirst().severity());
            assertEquals(ProcessLogOutput.Severity.WARN, monitor.getLines().getLast().severity());
        });
    }

    @Test
    void processExceptionStackTracesArePreservedAndColoredAcrossChunks() throws Exception {
        RuntimeException failure = new RuntimeException("failure", new IllegalArgumentException("cause"));
        failure.addSuppressed(new IllegalStateException("suppressed"));
        java.io.StringWriter trace = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(trace));
        byte[] bytes = trace.toString().getBytes(StandardCharsets.UTF_8);
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> {
            for (int i = 0; i < bytes.length; i += 7) {
                monitor.onErrorOutput(bytes, i, Math.min(7, bytes.length - i));
            }
            assertEquals(trace.toString(), monitor.getText());
            assertTrue(monitor.getLines().stream().allMatch(value -> value.severity() == ProcessLogOutput.Severity.ERROR));
            assertEquals(ProcessLogOutput.Severity.ERROR,
                    line("Exception in thread \"main\" java.lang.OutOfMemoryError: heap").severity());
            assertEquals(ProcessLogOutput.Severity.NORMAL, line("The Error is mentioned here").severity());
        });
    }

    @Test
    void colorsPrefixedAndExtendedStackFramesAsErrors() throws Exception {
        String trace = "[12:30:45] [main/ERROR]: Unexpected exception\n"
                + "[12:30:45] [main/INFO]: java.lang.RuntimeException: failure\n"
                + "[12:30:45] [main/INFO]: \tat example.Client.run(Client.java:42) ~[client.jar:?] {re:classloading}\n"
                + "at java.base/java.lang.Thread.run(Thread.java:10)\n"
                + "2026-11-14 12:30:45 INFO: Caused by: java.io.IOException: cause\n"
                + "[12:30:45] [main/INFO]: \tSuppressed: java.lang.IllegalStateException: suppressed\n"
                + "[12:30:45] [main/INFO]: \t... 12 common frames omitted\n";
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> {
            for (int i = 0; i < trace.length(); i += 11) {
                monitor.append(trace.substring(i, Math.min(i + 11, trace.length())));
            }
            assertEquals(trace, monitor.getText());
            assertTrue(monitor.getLines().stream().allMatch(value -> value.severity() == ProcessLogOutput.Severity.ERROR));
            monitor.append("[12:30:46] [main/INFO]: Recovered\n");
            assertEquals(ProcessLogOutput.Severity.NORMAL, monitor.getLines().getLast().severity());
        });
    }

    @Test
    void retentionDoesNotSplitSurrogatePairsOrLimitIndividualLines() throws Exception {
        ProcessLogOutput monitor = new ProcessLogOutput(false);
        FxTestThread.run(() -> {
            monitor.append("😀" + "x".repeat(ProcessLogOutput.MAX_LENGTH - 1));
            assertTrue(monitor.getText().length() <= ProcessLogOutput.MAX_LENGTH);
            assertFalse(Character.isLowSurrogate(monitor.getText().charAt(0)));
            assertEquals(1, monitor.getLines().size());
            assertEquals(monitor.getText(), monitor.getLines().getFirst().text());
            assertEquals(ProcessLogOutput.Severity.NORMAL,
                    line("a.".repeat(10000) + " ordinary message").severity());
            monitor.clear();
            monitor.append("\n".repeat(ProcessLogOutput.MAX_LENGTH + 10));
            assertEquals(ProcessLogOutput.MAX_LENGTH, monitor.getText().length());
            assertEquals(ProcessLogOutput.MAX_LENGTH, monitor.getLines().size());
        });
    }

    private static ProcessLogOutput.LogLine line(String value) {
        return ProcessLogOutput.parseLines(value).getFirst();
    }
}
