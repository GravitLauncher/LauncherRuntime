package pro.gravit.launcher.client.gui.overlay;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import pro.gravit.launcher.client.gui.JavaFXApplication;
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
    public static final long MAX_LENGTH = 163840;
    public static final int REMOVE_LENGTH = 1024;
    public Node layout;
    public Thread readThread;
    public TextArea output;
    public Process currentProcess;

    public DebugOverlay(JavaFXApplication application) throws IOException {
        super("overlay/debug/debug.fxml", application);
    }

    @Override
    protected void doInit() {
        layout = pane;
        output = (TextArea) layout.lookup("#output");
        ((ButtonBase) layout.lookup("#kill")).setOnAction((e) -> {
            if (currentProcess != null && currentProcess.isAlive()) currentProcess.destroyForcibly();
        });

        ((Label) layout.lookup("#version")).setText(ConsoleScene.getMiniLauncherInfo());
        ((ButtonBase) layout.lookup("#copy")).setOnAction((e) -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(output.getText());
            Clipboard clipboard = Clipboard.getSystemClipboard();
            clipboard.setContent(clipboardContent);
        });
        ((ButtonBase) layout.lookup("#close")).setOnAction((e) -> {
            //TODO
            Platform.exit();
        });
        ((ButtonBase) layout.lookup("#hide")).setOnAction((e) -> {
            //TODO
            if(this.currentStage != null) this.currentStage.hide();
        });
    }


    @Override
    public void reset() {

    }

    public void onProcess(Process process) {
        if (readThread != null && readThread.isAlive()) readThread.interrupt();
        if (currentProcess != null && currentProcess.isAlive()) currentProcess.destroyForcibly();
        readThread = CommonHelper.newThread("Client Process Console Reader", true, () -> {
            InputStream stream = process.getInputStream();
            byte[] buf = IOHelper.newBuffer();
            try {
                for (int length = stream.read(buf); length >= 0; length = stream.read(buf)) {
                    append(new String(buf, 0, length));
                }
                if(currentProcess.isAlive()) currentProcess.waitFor();
                append(String.format("Process exit code %d", currentProcess.exitValue()));
            } catch (IOException | InterruptedException e) {
                errorHandle(e);
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
    public void errorHandle(String error) {

    }

    @Override
    public void errorHandle(Throwable e) {
        if (!(e instanceof EOFException)) {
            LogHelper.error(e);
            if (LogHelper.isDebugEnabled())
                append(e.toString());
        }
        if (currentProcess != null && !currentProcess.isAlive()) {
            append(String.format("Process exit code %d", currentProcess.exitValue()));
        }
    }
}
