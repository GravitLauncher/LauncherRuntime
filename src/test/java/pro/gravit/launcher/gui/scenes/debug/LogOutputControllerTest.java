package pro.gravit.launcher.gui.scenes.debug;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;

class LogOutputControllerTest {
    @Test
    void repeatedUpwardScrollingMovesTowardsOlderWrappedOutput() throws Exception {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 900; i++) {
            log.append(i).append(' ').append("wrapped text ".repeat(i % 11 == 0 ? 250 : i % 7)).append('\n');
        }
        ViewportAnchor[] previous = new ViewportAnchor[1];
        double[] thumbSize = new double[1];
        verifyOnPulses(log.toString(), (fixture, pulse) -> {
            if (pulse < 3) return false;
            var first = visibleCells(fixture.output).getFirst();
            double visibleAmount = scrollbar(fixture.output).getVisibleAmount();
            ViewportAnchor current = new ViewportAnchor(first.getIndex(), first.getLayoutY());
            if (previous[0] != null) {
                ViewportAnchor last = previous[0];
                assertTrue(current.index < last.index || (current.index == last.index && current.y > last.y),
                        () -> "Upward wheel moved from " + last + " to " + current + " on pulse " + pulse);
                assertEquals(thumbSize[0], visibleAmount, "Scrolling static output must not resize the thumb");
            }
            previous[0] = current;
            thumbSize[0] = visibleAmount;
            first.fireEvent(lineScroll(first, 3));
            return pulse == 50;
        });
    }

    private record ViewportAnchor(int index, double y) { }

    @Test
    void keepsTheSameLineVisibleWhenNewOutputDiscardsOldLines() throws Exception {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < ProcessLogOutput.MAX_LINES; i++) log.append("message ").append(i).append('\n');
        String[] firstText = new String[1];
        double[] firstY = new double[1];
        verifyOnPulses(log.toString(), (fixture, pulse) -> {
            if (pulse == 3) {
                fixture.output.fireEvent(lineScroll(fixture.output, 10));
            } else if (pulse == 5) {
                var first = visibleCells(fixture.output).getFirst();
                firstText[0] = ((ProcessLogOutput.LogLine) first.getItem()).text();
                firstY[0] = first.getLayoutY();
                fixture.monitor.append("new message\n".repeat(100));
            } else if (pulse == 7) {
                var first = visibleCells(fixture.output).getFirst();
                assertFalse(fixture.controller.isFollowing());
                assertEquals(firstText[0], ((ProcessLogOutput.LogLine) first.getItem()).text());
                assertEquals(firstY[0], first.getLayoutY());
                return true;
            }
            return false;
        });
    }

    @Test
    void followingRemainsActiveAfterTheInitialScrollHasSettled() throws Exception {
        verifyOnPulses("message\n".repeat(200), (fixture, pulse) -> {
            if (pulse == 3) {
                assertTrue(fixture.controller.isFollowing());
                // Reproduce a later viewport adjustment without any user scroll input.
                ScrollBar bar = scrollbar(fixture.output);
                bar.setValue(bar.getMax() / 2);
            } else if (pulse == 5) {
                assertTrue(fixture.controller.isFollowing());
                assertEquals(1, position(fixture.output), 0.000001,
                        "Following must survive viewport adjustments after its first settled scroll");
                fixture.monitor.append("new wrapped line ".repeat(300));
            } else if (pulse == 6) {
                fixture.monitor.append("\nlatest line\n");
            } else if (pulse == 8) {
                assertTrue(fixture.controller.isFollowing());
                assertEquals(1, position(fixture.output), 0.000001);
                assertEquals(fixture.output.getItems().size() - 1, visibleCells(fixture.output).getLast().getIndex());
                return true;
            }
            return false;
        });
    }

    @Test
    void scrollingUpStaysPausedAcrossOutputAndLayoutPulses() throws Exception {
        verifyOnPulses("message\n".repeat(200), (fixture, pulse) -> {
            if (pulse == 1) {
                assertTrue(fixture.controller.isFollowing());
                // Change direction within one pulse.
                fixture.output.fireEvent(lineScroll(fixture.output, -1));
                fixture.output.fireEvent(lineScroll(fixture.output, 5));
                assertFalse(fixture.controller.isFollowing());
                assertTrue(position(fixture.output) < 0.999);
                fixture.monitor.append("new output while reading history\n");
            } else if (pulse == 3) {
                assertFalse(fixture.controller.isFollowing(), "New output must not resume following");
                assertTrue(position(fixture.output) < 0.999, "Layout and new output must not pull the view back down");
                fixture.output.fireEvent(lineScroll(fixture.output, -10000));
            } else if (pulse == 5) {
                assertTrue(fixture.controller.isFollowing(), "Scrolling back to the bottom resumes following");
                return true;
            }
            return false;
        });
    }

    @Test
    void finishingAnUpwardGestureDoesNotRestoreFollowing() throws Exception {
        verifyOnPulses("message\n".repeat(200), (fixture, pulse) -> {
            if (pulse == 3) {
                assertEquals(1, position(fixture.output), 0.000001);
                fixture.output.fireEvent(pixelScroll(ScrollEvent.SCROLL_STARTED, fixture.output, 0));
                fixture.output.fireEvent(pixelScroll(ScrollEvent.SCROLL, fixture.output, 1));
                fixture.output.fireEvent(pixelScroll(ScrollEvent.SCROLL_FINISHED, fixture.output, 0));
            } else if (pulse == 5) {
                assertFalse(fixture.controller.isFollowing(), "Ending a gesture must not undo upward scrolling");
                fixture.monitor.append("new output\n");
            } else if (pulse == 7) {
                assertFalse(fixture.controller.isFollowing());
                assertTrue(position(fixture.output) < 1);
                ScrollBar bar = scrollbar(fixture.output);
                assertNotNull(bar);
                bar.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, bar, 0, 0));
                bar.setValue(bar.getMax());
            } else if (pulse == 9) {
                assertFalse(fixture.controller.isFollowing(), "Following stays paused during a scrollbar drag");
                ScrollBar bar = scrollbar(fixture.output);
                assertEquals(bar.getMax(), bar.getValue(), "Scrollbar must stay at the drag destination");
                bar.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, bar, 0, 0));
            } else if (pulse == 11) {
                assertTrue(fixture.controller.isFollowing(), "Returning to the bottom with the scrollbar resumes following");
                return true;
            }
            return false;
        });
    }

    private static ScrollEvent pixelScroll(javafx.event.EventType<ScrollEvent> type, javafx.scene.Node target,
                                           double delta) {
        return new ScrollEvent(type, 20, 20, 20, 20,
                false, false, false, false, true, false, 0, delta, 0, delta,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, new PickResult(target, 20, 20));
    }

    private static ScrollEvent lineScroll(javafx.scene.Node target, double lines) {
        return new ScrollEvent(ScrollEvent.SCROLL, 20, 20, 20, 20,
                false, false, false, false, false, false, 0, 0, 0, 0,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.LINES, lines, 0, new PickResult(target, 20, 20));
    }

    @Test
    void firstStartupFollowsOutputAcrossRealLayoutPulsesWithoutSelection() throws Exception {
        verifyOnPulses("startup message\n".repeat(100), (fixture, pulse) -> {
            if (pulse == 1) {
                fixture.monitor.append("more startup messages\n".repeat(100));
            } else if (pulse == 2) {
                fixture.output.getScene().getWindow().setHeight(220);
            } else if (pulse == 3) {
                fixture.monitor.append("long final startup line ".repeat(300));
            } else if (pulse == 6) {
                    assertTrue(fixture.controller.isFollowing());
                assertTrue(fixture.output.getSelectionModel().isEmpty());
                var last = visibleCells(fixture.output).getLast();
                assertNotNull(last);
                assertEquals(fixture.output.getItems().size() - 1, last.getIndex());
                assertTrue(last.getBoundsInParent().getMaxY()
                        <= ((LogViewportSkin) fixture.output.getSkin()).viewportHeight() + 1, "The final text must be visible after startup layout");
                return true;
            }
            return false;
        });
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
            var first = visibleCells(output).getFirst();
            first.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    1, 1, 1, 1, false, new PickResult(first, 1, 1)));
            assertEquals(List.of(0, 1), output.getSelectionModel().getSelectedIndices());
            var menu = output.getContextMenu();
            menu.getOnShowing().handle(new WindowEvent(menu, WindowEvent.WINDOW_SHOWING));
            menu.getItems().get(0).fire();
            assertEquals("first" + System.lineSeparator() + "second", Clipboard.getSystemClipboard().getString());
            menu.getItems().get(1).fire();
            // Native clipboard services may normalize CRLF to LF when returning text.
            assertEquals(monitor.getText().replace("\r\n", "\n"),
                    Clipboard.getSystemClipboard().getString().replace("\r\n", "\n"));
            var third = visibleCells(output).get(2);
            third.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    1, 1, 1, 1, false, new PickResult(third, 1, 1)));
            assertEquals(List.of(2), output.getSelectionModel().getSelectedIndices());
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
            var first = visibleCells(output).getFirst();
            var third = visibleCells(output).get(2);
            Bounds from = first.localToScene(first.getBoundsInLocal());
            Bounds to = third.localToScene(third.getBoundsInLocal());
            first.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, first, from.getCenterX(), from.getCenterY()));
            first.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, first, to.getCenterX(), to.getCenterY()));
            first.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, first, to.getCenterX(), to.getCenterY()));
            assertEquals(List.of(0, 1, 2), output.getSelectionModel().getSelectedIndices());
            String expected = String.join(System.lineSeparator(), "first", "second", "third");
            assertEquals(expected, DebugScene.selectedText(output));
            output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, true));
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void followsBottomOfTallRowsAndResumesAfterScrollingBackDown() throws Exception {
        verifyOnPulses("short\n".repeat(100) + "long final row ".repeat(200), (fixture, pulse) -> {
            if (pulse == 3) {
                assertEquals(1, position(fixture.output), 0.000001);
                var last = visibleCells(fixture.output).getLast();
                assertEquals(fixture.output.getItems().size() - 1, last.getIndex());
                assertTrue(last.getBoundsInParent().getMaxY()
                        <= ((LogViewportSkin) fixture.output.getSkin()).viewportHeight() + 1, "The end of the tall final row must be visible");
                fixture.output.fireEvent(lineScroll(fixture.output, 5));
                assertFalse(fixture.controller.isFollowing());
                fixture.monitor.append("\nmore\n");
            } else if (pulse == 5) {
                assertTrue(position(fixture.output) < 1);
                fixture.output.fireEvent(lineScroll(fixture.output, -10000));
            } else if (pulse == 7) {
                assertTrue(fixture.controller.isFollowing());
                fixture.monitor.append("latest\n");
            } else if (pulse == 9) {
                assertEquals(1, position(fixture.output), 0.000001);
                fixture.output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.HOME,
                        false, false, false, false));
                assertFalse(fixture.controller.isFollowing());
                fixture.output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.END,
                        false, false, false, false));
            } else if (pulse == 11) {
                assertTrue(fixture.controller.isFollowing());
                assertEquals(1, position(fixture.output), 0.000001);
                return true;
            }
            return false;
        });
    }

    private static void verifyOnPulses(String initialText, BiPredicate<Fixture, Integer> verify) throws Exception {
        CompletableFuture<Void> verified = new CompletableFuture<>();
        Stage stage = FxTestThread.call(() -> {
            ProcessLogOutput monitor = new ProcessLogOutput(false, Runnable::run, Runnable::run);
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            output.setId("output");
            LogOutputController controller = DebugScene.configureOutput(output);
            output.setItems(monitor.getLines());
            monitor.append(initialText);
            Fixture fixture = new Fixture(monitor, output, controller);
            StackPane root = new StackPane(output);
            root.setId("output-detail");
            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().addAll(
                    Path.of("runtime/themes/dark/styles/variables.css").toUri().toString(),
                    Path.of("runtime/styles/global.css").toUri().toString(),
                    Path.of("runtime/scenes/debug/debug.css").toUri().toString());
            Stage window = new Stage();
            window.setScene(scene);
            scene.addPostLayoutPulseListener(new Runnable() {
                private int pulse;

                @Override
                public void run() {
                    try {
                        if (verify.test(fixture, ++pulse)) {
                            scene.removePostLayoutPulseListener(this);
                            verified.complete(null);
                        } else {
                            Platform.requestNextPulse();
                        }
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
            FxTestThread.run(() -> { stage.setScene(null); stage.close(); });
        }
    }

    private static List<ListCell<?>> visibleCells(ListView<?> output) {
        return output.lookupAll(".log-line").stream()
                .<ListCell<?>>map(node -> (ListCell<?>) node)
                .filter(cell -> cell.isVisible() && !cell.isEmpty())
                .sorted(Comparator.comparingInt(ListCell::getIndex))
                .toList();
    }

    private static ScrollBar scrollbar(ListView<?> output) {
        return (ScrollBar) output.lookup(".scroll-bar:vertical");
    }

    private static double position(ListView<?> output) {
        ScrollBar bar = scrollbar(output);
        return bar.getValue() / bar.getMax();
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
