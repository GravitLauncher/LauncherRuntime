package pro.gravit.launcher.gui.scenes.update;

import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualDownloaderTest {
    @Test
    void zeroDownloadTotalStaysIndeterminate() throws Exception {
        FxTestThread.run(() -> {
            VisualDownloader session = new VisualDownloader(null, ignored -> { });
            session.onTotalDownload(0);
            session.onCurrentDownloaded(512);

            assertEquals(-1.0, session.progressProperty().get());
            assertTrue(session.volumeProperty().get().contains("?"));
        });
    }

    @Test
    void cancelBeforeCallbackRunsLateCallbackExactlyOnce() throws Exception {
        FxTestThread.run(() -> {
            VisualDownloader session = new VisualDownloader(null, ignored -> { });
            AtomicInteger cancelCount = new AtomicInteger();

            session.cancel();
            session.onCanCancel(cancelCount::incrementAndGet);
            session.onCanCancel(cancelCount::incrementAndGet);

            assertTrue(session.isCancelled());
            assertFalse(session.canCancelProperty().get());
            assertFalse(session.activeProperty().get());
            assertEquals(1, cancelCount.get());
        });
    }

    @Test
    void invalidatedSessionIgnoresLateProgressWithoutBecomingCancelled() throws Exception {
        FxTestThread.run(() -> {
            VisualDownloader session = new VisualDownloader(null, ignored -> { });
            session.onTotalDownload(10);
            session.onCurrentDownloaded(4);
            assertEquals(0.4, session.progressProperty().get(), 0.0001);

            session.invalidate();
            session.onTotalDownload(100);
            session.onCurrentDownloaded(90);

            assertFalse(session.isCancelled());
            assertFalse(session.activeProperty().get());
            assertEquals(0.4, session.progressProperty().get(), 0.0001);
        });
    }

    @Test
    void nullCancellationCallbackDisablesCancellationWithoutConsumingALateCallback() throws Exception {
        FxTestThread.run(() -> {
            VisualDownloader session = new VisualDownloader(null, ignored -> { });
            session.onCanCancel(() -> { });
            assertTrue(session.canCancelProperty().get());
            session.onCanCancel(null);
            assertFalse(session.canCancelProperty().get());
            session.cancel();
            session.onCanCancel(null);
            AtomicInteger calls = new AtomicInteger();
            session.onCanCancel(calls::incrementAndGet);
            assertEquals(1, calls.get());
        });
    }

    @Test
    void successfulFinishDoesNotMarkSessionCancelled() throws Exception {
        FxTestThread.run(() -> {
            VisualDownloader session = new VisualDownloader(null, ignored -> { });
            session.onTotalDownload(10);
            session.onCurrentDownloaded(10);
            session.finish();

            assertFalse(session.isCancelled());
            assertFalse(session.activeProperty().get());
            assertEquals(1.0, session.progressProperty().get());
        });
    }
}
