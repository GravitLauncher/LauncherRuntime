package pro.gravit.launcher.client.gui.overlay;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.client.gui.scene.ConsoleScene;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class DebugOverlay extends AbstractOverlay {
    private static final long MAX_LENGTH = 163840;
    private static final int REMOVE_LENGTH = 1024;
    public Process currentProcess;
    public Thread writeParametersThread;
    private Thread readThread;
    private TextArea output;

    public DebugOverlay(JavaFXApplication application) {
        super("overlay/debug/debug.fxml", application);
    }

    @Override
    protected void doInit() {
        Node layout = pane;
        output = LookupHelper.lookup(layout, "#output");
        LookupHelper.<ButtonBase>lookup(layout, "#kill").setOnAction((e) -> {
            if (currentProcess != null && currentProcess.isAlive())
                currentProcess.destroyForcibly();
        });

        LookupHelper.<Label>lookup(layout, "#version").setText(ConsoleScene.getMiniLauncherInfo());
        LookupHelper.<ButtonBase>lookup(layout, "#copy").setOnAction((e) -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(output.getText());
            Clipboard clipboard = Clipboard.getSystemClipboard();
            clipboard.setContent(clipboardContent);
        });
        LookupHelper.<ButtonBase>lookup(layout, "#close").setOnAction((e) -> {
            //TODO
            Platform.exit();
        });
        LookupHelper.<ButtonBase>lookup(layout, "#back").setOnAction((e) -> {
            if (writeParametersThread != null && writeParametersThread.isAlive())
                return;
            if (currentProcess != null && currentProcess.isAlive()) {
                Process process = currentProcess;
                currentProcess = null;
                readThread.interrupt();
                writeParametersThread = null;
                readThread = null;
                try {
                    process.getErrorStream().close();
                    process.getInputStream().close();
                    process.getOutputStream().close();
                } catch (IOException ex) {
                    errorHandle(ex);
                }
            }
            try {
                if (currentStage != null) {
                    currentStage.getScene().hideOverlay(0, ex -> {
                    });
                    application.gui.updateOverlay.reset();
                }
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#hide").setOnAction((e) -> {
            //TODO
            if (this.currentStage != null)
                this.currentStage.hide();
        });
    }


    @Override
    public void reset() {
        output.clear();
    }

    public void onProcess(Process process) {
        if (readThread != null && readThread.isAlive())
            readThread.interrupt();
        if (currentProcess != null && currentProcess.isAlive())
            currentProcess.destroyForcibly();
        readThread = CommonHelper.newThread("Client Process Console Reader", true, () -> {
            InputStream stream = process.getInputStream();
            byte[] buf = IOHelper.newBuffer();
            try {
                for (int length = stream.read(buf); length >= 0; length = stream.read(buf)) {
                    append(new String(buf, 0, length));
                }
                if (currentProcess.isAlive()) currentProcess.waitFor();
                onProcessExit(currentProcess.exitValue());
            } catch (IOException e) {
                errorHandle(e);
            } catch (InterruptedException ignored) {

            }
        });
        readThread.start();
        currentProcess = process;
    }

    public void append(String text) {
        ContextHelper.runInFxThreadStatic(() -> {
            if (output.lengthProperty().get() > MAX_LENGTH)
                output.deleteText(0, REMOVE_LENGTH);
            output.appendText(text);
        });
    }

    @Override
    public void errorHandle(Throwable e) {
        if (!(e instanceof EOFException)) {
            LogHelper.error(e);
            if (LogHelper.isDebugEnabled())
                append(e.toString());
        }
        if (currentProcess != null && !currentProcess.isAlive()) {
            onProcessExit(currentProcess.exitValue());
        }
    }

    private void onProcessExit(int code) {
        append(String.format("Process exit code %d", code));
        if (writeParametersThread != null) writeParametersThread.interrupt();
    }
}
