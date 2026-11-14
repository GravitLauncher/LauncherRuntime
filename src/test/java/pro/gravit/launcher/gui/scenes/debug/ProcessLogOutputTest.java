package pro.gravit.launcher.gui.scenes.debug;

import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
