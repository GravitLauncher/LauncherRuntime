package pro.gravit.launcher.gui.scenes.debug;

import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.WindowEvent;
import javafx.stage.Stage;
import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import static org.junit.jupiter.api.Assertions.*;

class LogOutputControllerTest {
    @Test
    void firstStartupFollowsOutputAcrossRealLayoutPulsesWithoutSelection() throws Exception {
        CompletableFuture<Void> verified = new CompletableFuture<>();
        Stage stage = FxTestThread.call(() -> {
            ProcessLogOutput monitor = new ProcessLogOutput(false, Runnable::run, Runnable::run);
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            LogOutputController controller = DebugScene.configureOutput(output);
            output.setItems(monitor.getLines());
            // Output arrives before the view has a Scene, skin, or viewport dimensions.
            monitor.append("startup message\n".repeat(100));
            Stage window = new Stage();
            Scene scene = new Scene(new StackPane(output), 400, 300);
            window.setScene(scene);
            scene.addPostLayoutPulseListener(new Runnable() {
                private int pulse;

                @Override
                public void run() {
                    try {
                        pulse++;
                        if (pulse == 1) monitor.append("more startup messages\n".repeat(100));
                        if (pulse == 2) window.setHeight(220);
                        if (pulse == 3) monitor.append("long final startup line ".repeat(300));
                        if (pulse >= 6) {
                            VirtualFlow<?> flow = (VirtualFlow<?>) output.lookup(".virtual-flow");
                            assertTrue(controller.isFollowing());
                            assertTrue(output.getSelectionModel().isEmpty());
                            var last = flow.getLastVisibleCell();
                            assertNotNull(last);
                            assertEquals(output.getItems().size() - 1, last.getIndex());
                            assertTrue(flow.sceneToLocal(last.localToScene(last.getBoundsInLocal())).getMaxY()
                                    <= flow.getViewportLength() + 1, "The final text must be visible after startup layout");
                            scene.removePostLayoutPulseListener(this);
                            verified.complete(null);
                        } else Platform.requestNextPulse();
                    } catch (Throwable failure) {
                        scene.removePostLayoutPulseListener(this);
                        verified.completeExceptionally(failure);
                    }
                }
            });
            window.show();
            Platform.requestNextPulse();
            return window;
        });
        try {
            verified.get(15, TimeUnit.SECONDS);
        } finally {
            FxTestThread.run(() -> {
                // Detaching the scene also stops the controller's pulse listener.
                stage.setScene(null);
                stage.close();
            });
        }
    }

    @Test
    void contextMenuCopiesSelectionOrFullTextAndResetsFollowing() throws Exception {
        FxTestThread.run(() -> {
            ProcessLogOutput monitor = new ProcessLogOutput(false, Runnable::run, Runnable::run);
            monitor.append("first\r\nsecond\r\nthird\r\n");
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            LogOutputController controller = DebugScene.configureOutput(output);
            controller.installContextMenu(key -> key, monitor::getText);
            output.setItems(monitor.getLines());
            StackPane root = new StackPane(output);
            new Scene(root, 400, 300);
            root.applyCss();
            root.layout();
            output.getSelectionModel().selectIndices(0, 1);
            // Exercise context targeting without opening a popup window in this test.
            output.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> event.consume());
            VirtualFlow<?> flow = (VirtualFlow<?>) output.lookup(".virtual-flow");
            var first = flow.getVisibleCell(0);
            first.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    1, 1, 1, 1, false, new PickResult(first, 1, 1)));
            assertEquals(java.util.List.of(0, 1), output.getSelectionModel().getSelectedIndices());
            var menu = output.getContextMenu();
            menu.getOnShowing().handle(new WindowEvent(menu, WindowEvent.WINDOW_SHOWING));
            menu.getItems().get(0).fire();
            assertEquals("first" + System.lineSeparator() + "second", Clipboard.getSystemClipboard().getString());
            menu.getItems().get(1).fire();
            assertEquals(monitor.getText(), Clipboard.getSystemClipboard().getString());
            var third = flow.getVisibleCell(2);
            third.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    1, 1, 1, 1, false, new PickResult(third, 1, 1)));
            assertEquals(java.util.List.of(2), output.getSelectionModel().getSelectedIndices());
            assertFalse(controller.isFollowing());
            menu.getItems().get(3).fire();
            assertTrue(output.getSelectionModel().isEmpty());
            assertTrue(controller.isFollowing(), "All rows fit, so clearing selection must resume following");
            output.getSelectionModel().select(1);
            menu.getItems().get(4).fire();
            assertTrue(output.getSelectionModel().isEmpty());
            assertTrue(controller.isFollowing());
            output.getSelectionModel().select(0);
            output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            assertTrue(output.getSelectionModel().isEmpty());
            assertTrue(controller.isFollowing());
            menu.getOnShowing().handle(new WindowEvent(menu, WindowEvent.WINDOW_SHOWING));
            assertTrue(menu.getItems().get(0).isDisable());
            assertTrue(menu.getItems().get(3).isDisable());
        });
    }

    @Test
    void mouseDragSelectsMultipleRowsAndCopiesThemInOrder() throws Exception {
        FxTestThread.run(() -> {
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            DebugScene.configureOutput(output);
            for (String value : new String[] {"first", "second", "third", "fourth"}) {
                output.getItems().add(new ProcessLogOutput.LogLine(value, ProcessLogOutput.Severity.NORMAL));
            }
            StackPane root = new StackPane(output);
            new Scene(root, 400, 300);
            root.applyCss();
            root.layout();
            VirtualFlow<?> flow = (VirtualFlow<?>) output.lookup(".virtual-flow");
            var first = flow.getVisibleCell(0);
            var third = flow.getVisibleCell(2);
            Bounds from = first.localToScene(first.getBoundsInLocal());
            Bounds to = third.localToScene(third.getBoundsInLocal());
            first.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, first, from.getCenterX(), from.getCenterY()));
            first.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, first, to.getCenterX(), to.getCenterY()));
            first.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, first, to.getCenterX(), to.getCenterY()));
            assertEquals(java.util.List.of(0, 1, 2), output.getSelectionModel().getSelectedIndices());
            String expected = String.join(System.lineSeparator(), "first", "second", "third");
            assertEquals(expected, DebugScene.selectedText(output));
            output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, true));
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void followsBottomOfTallRowsAndResumesAfterScrollingBackDown() throws Exception {
        Fixture fixture = FxTestThread.call(() -> {
            ProcessLogOutput monitor = new ProcessLogOutput(false, Runnable::run, Runnable::run);
            monitor.append("short\n".repeat(100) + "long final row ".repeat(200));
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            LogOutputController controller = DebugScene.configureOutput(output);
            output.setItems(monitor.getLines());
            StackPane root = new StackPane(output);
            new Scene(root, 400, 300);
            root.applyCss();
            root.layout();
            return new Fixture(monitor, output, controller);
        });
        FxTestThread.run(() -> {
            VirtualFlow<?> flow = flow(fixture);
            assertEquals(1, flow.getPosition(), 0.000001);
            assertEquals(fixture.output.getItems().size() - 1, flow.getLastVisibleCell().getIndex());
            Bounds last = flow.getLastVisibleCell().localToScene(flow.getLastVisibleCell().getBoundsInLocal());
            Bounds view = fixture.output.localToScene(fixture.output.getBoundsInLocal());
            assertTrue(last.getMaxY() <= view.getMaxY() + 1, "The end of the tall final row must be visible");
            flow.setPosition(0.2);
            flow.layout();
            fixture.controller.resumeAtBottom();
            assertFalse(fixture.controller.isFollowing());
            fixture.monitor.append("\nmore\n");
        });
        FxTestThread.run(() -> {
            VirtualFlow<?> flow = flow(fixture);
            assertTrue(flow.getPosition() < 0.99);
            flow.setPosition(1);
            flow.layout();
            fixture.controller.resumeAtBottom();
            assertTrue(fixture.controller.isFollowing());
            fixture.monitor.append("latest\n");
        });
        FxTestThread.run(() -> {
            assertEquals(1, flow(fixture).getPosition(), 0.000001);
            fixture.output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.HOME,
                    false, false, false, false));
            assertFalse(fixture.controller.isFollowing());
            fixture.output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.END,
                    false, false, false, false));
        });
        FxTestThread.run(() -> assertEquals(1, flow(fixture).getPosition(), 0.000001));
    }

    private static VirtualFlow<?> flow(Fixture fixture) {
        return (VirtualFlow<?>) fixture.output.lookup(".virtual-flow");
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, javafx.scene.Node target,
                                    double x, double y) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, type != MouseEvent.MOUSE_RELEASED,
                false, false, false, false, true, new PickResult(target, x, y));
    }

    private record Fixture(ProcessLogOutput monitor, ListView<ProcessLogOutput.LogLine> output,
                           LogOutputController controller) { }
}
