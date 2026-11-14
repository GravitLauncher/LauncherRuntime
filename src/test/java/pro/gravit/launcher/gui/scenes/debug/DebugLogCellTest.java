package pro.gravit.launcher.gui.scenes.debug;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.core.ui.FxTestThread;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DebugLogCellTest {
    @Test
    void emptyAndReusedRowsAlwaysHaveAPositiveHeight() throws Exception {
        FxTestThread.run(() -> {
            DebugScene.LogCell cell = new DebugScene.LogCell();
            StackPane root = new StackPane(cell);
            root.setId("output");
            Scene scene = new Scene(root, 400, 200);
            scene.getStylesheets().addAll(
                    Path.of("runtime/themes/dark/styles/variables.css").toUri().toString(),
                    Path.of("runtime/scenes/debug/debug.css").toUri().toString());
            root.applyCss();
            assertTrue(cell.prefHeight(400) >= 1, "Initial filler cells need a positive height");
            cell.updateItem(new ProcessLogOutput.LogLine("long ".repeat(100), ProcessLogOutput.Severity.NORMAL), false);
            assertTrue(cell.prefHeight(400) >= 1);
            cell.updateItem(null, true);
            assertTrue(cell.prefHeight(400) >= 1, "Recycled filler cells must not collapse to zero");
            cell.updateItem(new ProcessLogOutput.LogLine("", ProcessLogOutput.Severity.NORMAL), false);
            assertTrue(cell.prefHeight(400) >= 1);
        });
    }

    @Test
    void changingLogSizesAndScrollingDoNotEmitVirtualFlowSizeWarnings() throws Exception {
        java.util.List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("javafx.scene.control");
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                if (record.getMessage().contains("index exceeds maxCellCount")) warnings.add(record.getMessage());
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            FxTestThread.run(() -> {
                ListView<ProcessLogOutput.LogLine> output = new ListView<>();
                output.setId("output");
                LogOutputController controller = DebugScene.configureOutput(output);
                StackPane root = new StackPane(output);
                Scene scene = new Scene(root, 400, 300);
                scene.getStylesheets().addAll(
                        Path.of("runtime/themes/dark/styles/variables.css").toUri().toString(),
                        Path.of("runtime/scenes/debug/debug.css").toUri().toString());
                root.applyCss();
                for (int count : new int[] {1, 10, 1000, 0, 20, 10000, 2}) {
                    var rows = new java.util.ArrayList<ProcessLogOutput.LogLine>();
                    for (int i = 0; i < count; i++) rows.add(new ProcessLogOutput.LogLine(
                            i % 3 == 0 ? "" : i % 3 == 1 ? "wrapped ".repeat(100) : "short",
                            ProcessLogOutput.Severity.NORMAL));
                    output.getItems().setAll(rows);
                    root.layout();
                    output.scrollTo(Math.max(0, count - 1));
                    output.layout();
                    var flow = (javafx.scene.control.skin.VirtualFlow<?>) output.lookup(".virtual-flow");
                    flow.setPosition(1);
                    flow.layout();
                    root.requestLayout();
                    root.layout();
                }
                controller.dispose();
            });
            assertTrue(warnings.isEmpty(), "VirtualFlow warnings: " + warnings);
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void wrappedRowsContainTheirTextAfterResizingAndReuse() throws Exception {
        FxTestThread.run(() -> {
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            DebugScene.configureOutput(output);
            output.setId("output");
            for (int i = 0; i < 30; i++) {
                output.getItems().add(new ProcessLogOutput.LogLine(
                        (i % 2 == 0 ? "wrapped message ".repeat(1000) : "short line"),
                        ProcessLogOutput.Severity.NORMAL));
            }
            StackPane root = new StackPane(output);
            Scene scene = new Scene(root, 600, 400);
            scene.getStylesheets().addAll(
                    Path.of("runtime/themes/dark/styles/variables.css").toUri().toString(),
                    Path.of("runtime/styles/global.css").toUri().toString(),
                    Path.of("runtime/scenes/debug/debug.css").toUri().toString());
            for (int width : new int[] {600, 220, 450}) {
                root.resize(width, 400);
                root.applyCss();
                root.layout();
                output.scrollTo(10);
                root.layout();
                for (var node : output.lookupAll(".log-line")) {
                    DebugScene.LogCell cell = (DebugScene.LogCell) node;
                    if (cell.isEmpty()) continue;
                    Label label = (Label) cell.getGraphic();
                    assertTrue(label.getBoundsInParent().getMinY() >= -0.5);
                    assertTrue(label.getHeight() + 0.5 >= label.prefHeight(label.getWidth()),
                            "Wrapped text must fit the allocated label height");
                    var textNode = label.lookup(".text");
                    assertNotNull(textNode);
                    assertTrue(textNode.getBoundsInParent().getMaxY() <= label.getHeight() + 0.5,
                            "Rendered text must fit the label");
                    assertTrue(label.getBoundsInParent().getMaxY() <= cell.getHeight() + 0.5,
                            "Text height " + label.getHeight() + " exceeds row height " + cell.getHeight());
                }
            }
        });
    }

    @Test
    void selectsAndCopiesWholeLinesWhileCompleteLinesKeepSelection() throws Exception {
        FxTestThread.run(() -> {
            ProcessLogOutput monitor = new ProcessLogOutput(false, Runnable::run, Runnable::run);
            monitor.append("first\n\nthird\n");
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            DebugScene.configureOutput(output);
            output.setItems(monitor.getLines());
            output.getSelectionModel().selectIndices(0, 1, 2);
            assertEquals("first" + System.lineSeparator() + System.lineSeparator() + "third",
                    DebugScene.selectedText(output));
            monitor.append("fourth\n");
            assertEquals(3, output.getSelectionModel().getSelectedIndices().size());
            output.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C,
                    false, true, false, true));
            assertEquals(DebugScene.selectedText(output), Clipboard.getSystemClipboard().getString());
            output.getSelectionModel().clearAndSelect(3);
            assertEquals("fourth", DebugScene.selectedText(output));
            monitor.clear();
            assertTrue(output.getSelectionModel().isEmpty());
            assertEquals("", DebugScene.selectedText(output));
        });
    }

    @Test
    void longLinesWrapWithoutChangingCopiedText() throws Exception {
        FxTestThread.run(() -> {
            String text = "long message ".repeat(1000);
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            DebugScene.configureOutput(output);
            output.getItems().add(new ProcessLogOutput.LogLine(text, ProcessLogOutput.Severity.NORMAL));
            StackPane root = new StackPane(output);
            new Scene(root, 300, 200);
            root.applyCss();
            root.layout();
            DebugScene.LogCell cell = (DebugScene.LogCell) output.lookup(".log-line");
            Label label = (Label) cell.getGraphic();
            assertEquals(text.substring(0, DebugScene.LogCell.MAX_PREVIEW_LENGTH) + "…", label.getText());
            assertTrue(label.getHeight() > 100);
            assertTrue(label.getWidth() < output.getWidth());
            assertTrue(label.prefHeight(150) > label.prefHeight(250));
            output.getSelectionModel().select(0);
            assertEquals(text, DebugScene.selectedText(output));
        });
    }

    @Test
    void darkThemeKeepsLogBackgroundTransparentAndTextReadable() throws Exception {
        FxTestThread.run(() -> {
            ListView<ProcessLogOutput.LogLine> output = new ListView<>();
            output.setId("output");
            output.setCellFactory(view -> new DebugScene.LogCell());
            output.getItems().setAll(
                    new ProcessLogOutput.LogLine("normal", ProcessLogOutput.Severity.NORMAL),
                    new ProcessLogOutput.LogLine("warning", ProcessLogOutput.Severity.WARN),
                    new ProcessLogOutput.LogLine("error", ProcessLogOutput.Severity.ERROR),
                    new ProcessLogOutput.LogLine("debug", ProcessLogOutput.Severity.DEBUG));
            StackPane root = new StackPane(output);
            root.setId("output-detail");
            Scene scene = new Scene(root, 500, 300);
            scene.getStylesheets().addAll(
                    Path.of("runtime/themes/dark/styles/variables.css").toUri().toString(),
                    Path.of("runtime/styles/global.css").toUri().toString(),
                    Path.of("runtime/scenes/debug/debug.css").toUri().toString());
            root.applyCss();
            root.layout();

            assertFalse(output.getBackground().getFills().isEmpty());
            output.getBackground().getFills().forEach(fill ->
                    assertEquals(Color.TRANSPARENT, fill.getFill()));
            int visibleLines = 0;
            for (var node : output.lookupAll(".log-line")) {
                DebugScene.LogCell cell = (DebugScene.LogCell) node;
                if (cell.isEmpty()) continue;
                visibleLines++;
                Color expected = switch (cell.getItem().severity()) {
                    case NORMAL -> Color.web("#dddddd");
                    case WARN -> Color.web("#f2d35e");
                    case ERROR -> Color.web("#DB4D4D");
                    case DEBUG -> Color.web("#f0a04b");
                };
                assertEquals(expected, ((Label) cell.getGraphic()).getTextFill());
                cell.getBackground().getFills().forEach(fill ->
                        assertEquals(Color.TRANSPARENT, fill.getFill()));
            }
            assertEquals(4, visibleLines);
        });
    }

    @Test
    void reusedCellsResetSeverityAndWrapMessages() throws Exception {
        FxTestThread.run(() -> {
            DebugScene.LogCell cell = new DebugScene.LogCell();
            StackPane root = new StackPane(cell);
            root.setId("output");
            Scene scene = new Scene(root, 300, 200);
            scene.getStylesheets().addAll(
                    Path.of("runtime/themes/dark/styles/variables.css").toUri().toString(),
                    Path.of("runtime/scenes/debug/debug.css").toUri().toString());

            cell.updateItem(new ProcessLogOutput.LogLine("warning", ProcessLogOutput.Severity.WARN), false);
            root.applyCss();
            Label message = (Label) cell.getGraphic();
            assertTrue(message.isWrapText());
            assertEquals(Color.web("#f2d35e"), message.getTextFill());

            cell.updateItem(new ProcessLogOutput.LogLine("error", ProcessLogOutput.Severity.ERROR), false);
            root.applyCss();
            assertFalse(cell.getStyleClass().contains("log-warn"));
            assertEquals(Color.web("#DB4D4D"), message.getTextFill());

            cell.updateItem(new ProcessLogOutput.LogLine("details", ProcessLogOutput.Severity.DEBUG), false);
            root.applyCss();
            assertEquals(Color.web("#f0a04b"), message.getTextFill());

            cell.updateItem(new ProcessLogOutput.LogLine("normal", ProcessLogOutput.Severity.NORMAL), false);
            root.applyCss();
            assertFalse(cell.getStyleClass().contains("log-debug"));
            assertEquals(Color.web("#dddddd"), message.getTextFill());

            cell.updateItem(null, true);
            assertNull(cell.getGraphic());
            assertNull(message.getText());
        });
    }
}
