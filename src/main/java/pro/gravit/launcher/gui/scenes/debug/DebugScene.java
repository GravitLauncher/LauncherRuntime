package pro.gravit.launcher.gui.scenes.debug;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.gui.components.BasicUserControls;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.JavaRuntimeModule;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;

import java.io.EOFException;

public class DebugScene extends FxScene {
    private volatile ProcessLogOutput processLogOutput;
    private ListView<ProcessLogOutput.LogLine> output;

    public DebugScene(JavaFXApplication application) {
        super("scenes/debug/debug.fxml", application);
        this.isResetOnShow = true;
        this._basicUserControlConstructor = DebugBasicUserControls::new;
    }

    @Override
    protected void doInit() {
        output = LookupHelper.lookup(layout, ListView.class, "#output");
        LogOutputController outputController = configureOutput(output);
        scope.add(outputController::dispose);
        outputController.installContextMenu(application::getTranslation, () -> processLogOutput.getText());
        if (processLogOutput == null) {
            processLogOutput = new ProcessLogOutput(!application.runtimeSettings.globalSettings.debugAllClients);
        }
        bindOutput();
        LookupHelper.lookupIfPossible(header, ButtonBase.class, "#controls", "#kill").ifPresent((x) -> x.setOnAction((e) -> {
            processLogOutput.terminate();
        }));

        LookupHelper.lookupIfPossible(layout, Label.class, "#version")
                    .ifPresent((v) -> v.setText(JavaRuntimeModule.getMiniLauncherInfo()));
        LookupHelper.lookupIfPossible(header, ButtonBase.class, "#controls", "#copy").ifPresent((x) -> x.setOnAction((e) -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(processLogOutput.getText());
            Clipboard.getSystemClipboard().setContent(content);
        }));
        LookupHelper.lookup(header, ButtonBase.class, "#back").setOnAction((e) -> {
            try {
                switchToBackScene();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });

    }


    @Override
    public void reset() {
        // Keep output available when returning to a running or recently finished process.
    }

    public void run(LauncherBackendAPI.ReadyProfile readyProfile) {
        if (processLogOutput == null || processLogOutput.isRunning()) return;
        processLogOutput = new ProcessLogOutput(!application.runtimeSettings.globalSettings.debugAllClients);
        bindOutput();
        processLogOutput.clear();
        processLogOutput.markStarting();
        try {
            readyProfile.run(processLogOutput);
        } catch (Throwable e) {
            processLogOutput.markLaunchFailed();
            errorHandle(e);
        }
    }

    public boolean isRunning() {
        return processLogOutput != null && processLogOutput.isRunning();
    }

    public void append(String text) {
        if (processLogOutput != null) processLogOutput.append(text);
    }

    @Override
    public void errorHandle(Throwable e) {
        if (!(e instanceof EOFException) && processLogOutput != null) {
            processLogOutput.append(e.toString());
        }
    }

    @Override
    public String getName() {
        return "debug";
    }

    static LogOutputController configureOutput(ListView<ProcessLogOutput.LogLine> output) {
        LogViewportSkin viewport = new LogViewportSkin(output);
        output.setSkin(viewport);
        return new LogOutputController(output, viewport);
    }

    static String selectedText(ListView<ProcessLogOutput.LogLine> output) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (int index : output.getSelectionModel().getSelectedIndices()) {
            if (!first) result.append(System.lineSeparator());
            result.append(output.getItems().get(index).text());
            first = false;
        }
        return result.toString();
    }

    private void bindOutput() {
        output.setItems(processLogOutput.getLines());
    }

    static final class LogCell extends ListCell<ProcessLogOutput.LogLine> {
        static final int MAX_PREVIEW_LENGTH = 8192;
        private final Label message = new Label();

        LogCell() {
            getStyleClass().add("log-line");
            setPrefWidth(0);
            message.setWrapText(true);
            message.setTextOverrun(OverrunStyle.CLIP);
            message.setMinHeight(USE_PREF_SIZE);
            message.setMinWidth(0);
            setMinHeight(USE_PREF_SIZE);
        }

        private double contentWidth(double width) {
            return Math.max(1, width - snappedLeftInset() - snappedRightInset());
        }

        @Override
        protected double computePrefHeight(double width) {
            double height = message.prefHeight(contentWidth(width >= 0 ? width : getWidth()));
            return Math.ceil(Math.max(Math.max(1, message.getFont().getSize()), height)
                    + snappedTopInset() + snappedBottomInset());
        }

        void prepareMeasurement(Runnable invalidate) {
            setManaged(false);
            setVisible(false);
            setGraphic(message);
            insetsProperty().addListener(observable -> invalidate.run());
            message.fontProperty().addListener(observable -> invalidate.run());
        }

        double measure(String text, double width) {
            message.setText(preview(text));
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (getGraphic() != null) {
                // Measure and lay out at exactly the same width so wrapped text fits its row.
                double width = contentWidth(getWidth());
                message.resizeRelocate(snappedLeftInset(), snappedTopInset(), width, message.prefHeight(width));
            }
        }

        @Override
        protected void updateItem(ProcessLogOutput.LogLine item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("log-warn", "log-error", "log-debug");
            setText(null);
            if (empty || item == null) {
                message.setText(null);
                setGraphic(null);
                return;
            }
            String style = switch (item.severity()) {
                case WARN -> "log-warn";
                case ERROR -> "log-error";
                case DEBUG -> "log-debug";
                case NORMAL -> null;
            };
            if (style != null) getStyleClass().add(style);
            message.setText(preview(item.text()));
            setGraphic(message);
        }

        private static String preview(String text) {
            if (text.isEmpty()) return " ";
            if (text.length() <= MAX_PREVIEW_LENGTH) return text;
            int end = MAX_PREVIEW_LENGTH;
            if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            return text.substring(0, end) + "…";
        }
    }

    private class DebugBasicUserControls extends BasicUserControls {

        public DebugBasicUserControls(Pane layout, JavaFXApplication application) {
            super(layout, application);
        }

        @Override
        protected void onExit() {
            if(processLogOutput == null || processLogOutput.isReadyToExit()) {
                super.onExit();
            }
            else {
                application.messageManager.showApplyDialog(
                        application.getTranslation("runtime.scenes.debug.forceExitDialog.header"),
                        application.getTranslation("runtime.scenes.debug.forceExitDialog.description"),
                        () -> {
                            processLogOutput.terminate();
                            super.onExit();
                        }, () -> {}, true);
            }
        }
    }
}
