package pro.gravit.launcher.gui.scenes.debug;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.application.Platform;
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
    private TextArea output;

    public DebugScene(JavaFXApplication application) {
        super("scenes/debug/debug.fxml", application);
        this.isResetOnShow = true;
        this._basicUserControlConstructor = DebugBasicUserControls::new;
    }

    @Override
    protected void doInit() {
        if (output != null) output.textProperty().unbind();
        output = LookupHelper.lookup(layout, TextArea.class, "#output");
        if (processLogOutput == null) {
            processLogOutput = new ProcessLogOutput(!application.runtimeSettings.globalSettings.debugAllClients);
        }
        output.textProperty().bind(processLogOutput.textProperty());
        output.textProperty().addListener((observable, previousText, currentText) -> {
            if (currentText == null) return;
            TextArea currentOutput = output;
            currentOutput.positionCaret(currentText.length());
            Platform.runLater(() -> {
                if (output == currentOutput) currentOutput.setScrollTop(Double.MAX_VALUE);
            });
        });
        LookupHelper.lookupIfPossible(header, ButtonBase.class, "#controls", "#kill").ifPresent((x) -> x.setOnAction((e) -> {
            processLogOutput.terminate();
        }));

        LookupHelper.lookupIfPossible(layout, Label.class, "#version")
                    .ifPresent((v) -> v.setText(JavaRuntimeModule.getMiniLauncherInfo()));
        LookupHelper.lookupIfPossible(header, ButtonBase.class, "#controls", "#copy").ifPresent((x) -> x.setOnAction((e) -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(output.getText());
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
        output.textProperty().unbind();
        output.textProperty().bind(processLogOutput.textProperty());
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
