package pro.gravit.launcher.gui.core.impl;

import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.LauncherConfig;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {
    private static JavaFXApplication application() {
        Launcher.setConfig(new LauncherConfig("http://localhost", null, null, Map.of(), "test"));
        JavaFXApplication app = new JavaFXApplication();
        app.gui = new GuiObjectsContainer(app);
        return app;
    }

    @Test void navigationOwnsHooksAndPreservesCurrentViewOnFailure() throws Exception {
        FxTestThread.call(() -> {
            JavaFXApplication app = application();
            TestStage stage = new TestStage(app);
            try {
                TestView first = app.gui.registerComponent(ignored -> new TestView(app, "first", false));
                TestView second = app.gui.registerComponent(ignored -> new TestView(app, "second", false));
                stage.setScene(first, true);
                stage.setScene(second, true);
                assertEquals(1, first.shows);
                assertEquals(1, first.hides);
                assertSame(first, stage.back());
                assertEquals(2, first.shows);
                assertEquals(1, second.hides);
                TestView broken = new TestView(app, "broken", true);
                assertThrows(IllegalStateException.class, () -> stage.setScene(broken, true));
                assertSame(first, stage.getVisualComponent());
                assertEquals(1, first.hides);
                assertNull(stage.back());
            } finally { stage.close(); app.workers.shutdownNow(); }
            return null;
        });
    }

    @Test void inputHandlesAreNestedAndIdempotent() throws Exception {
        FxTestThread.call(() -> {
            JavaFXApplication app = application();
            TestStage stage = new TestStage(app);
            try {
                TestView view = new TestView(app, "test", false);
                stage.setScene(view, true);
                AutoCloseable first = stage.blockInput();
                AutoCloseable second = stage.blockInput();
                assertTrue(view.getLayout().isDisabled());
                first.close(); first.close();
                assertTrue(view.getLayout().isDisabled());
                second.close();
                assertFalse(view.getLayout().isDisabled());
            } finally { stage.close(); app.workers.shutdownNow(); }
            return null;
        });
    }

    @Test void interruptingAnOverlayCompletesCallbacksAndReleasesInput() throws Exception {
        CompletableFuture<Void> hidden = new CompletableFuture<>();
        AtomicInteger shown = new AtomicInteger();
        AtomicInteger interruptedHide = new AtomicInteger();
        var fixture = FxTestThread.call(() -> {
            JavaFXApplication app = application();
            TestStage stage = new TestStage(app);
            stage.setScene(new TestView(app, "test", false), true);
            TestOverlay overlay = new TestOverlay(app);
            overlay.hide(0, event -> interruptedHide.incrementAndGet());
            overlay.show(stage, event -> shown.incrementAndGet());
            overlay.hide(0, event -> interruptedHide.incrementAndGet());
            overlay.show(stage, event -> shown.incrementAndGet());
            overlay.hide(0, event -> hidden.complete(null));
            return new Object[] { app, stage, overlay };
        });
        try {
            hidden.get(5, TimeUnit.SECONDS);
            FxTestThread.run(() -> {
                TestStage stage = (TestStage) fixture[1];
                assertFalse(stage.getVisualComponent().getLayout().isDisabled());
                assertEquals(2, shown.get());
                assertEquals(2, interruptedHide.get());
                ((TestOverlay) fixture[2]).dispose();
            });
        } finally { FxTestThread.run(() -> { ((TestStage) fixture[1]).close(); ((JavaFXApplication) fixture[0]).workers.shutdownNow(); }); }
    }

    private static final class TestStage extends FxStage {
        TestStage(JavaFXApplication app) { super(app, new Stage()); }
        @Override public void resetStyles() { }
    }
    private static final class TestView extends FxComponent {
        private final String name;
        private final boolean fail;
        int shows;
        int hides;
        TestView(JavaFXApplication app, String name, boolean fail) { super(new Pane(), app); this.name = name; this.fail = fail; }
        @Override protected void doInit() { if (fail) throw new IllegalStateException("expected failure"); layout.setPrefSize(100, 100); }
        @Override public void reset() { }
        @Override public String getName() { return name; }
        @Override public void onShow() { super.onShow(); shows++; }
        @Override public void onHide() { super.onHide(); hides++; }
    }
    private static final class TestOverlay extends FxOverlay {
        TestOverlay(JavaFXApplication app) { super("unused", app); layout = new Pane(); }
        @Override protected javafx.scene.Parent getFxmlRoot() { return layout; }
        @Override protected void doInit() { }
        @Override public void reset() { }
        @Override public String getName() { return "overlay"; }
    }
}
