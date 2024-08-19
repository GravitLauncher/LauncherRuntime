package pro.gravit.launcher.gui.scenes.debug;

import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import pro.gravit.launcher.gui.impl.ContextHelper;
import pro.gravit.launcher.gui.service.LaunchService;

public class ProcessLogOutput implements LaunchService.ClientInstance.ProcessListener {
    static final long MAX_LENGTH = 1024 * 256;
    static final int REMOVE_LENGTH = 1024 * 16;
    private final TextArea output;
    private final Object syncObject = new Object();
    private String appendString = "";
    private boolean isOutputRunned;

    public ProcessLogOutput(TextArea output) {
        this.output = output;
    }

    public String getText() {
        return output.getText();
    }

    public void clear() {
        output.clear();
    }

    public void copyToClipboard() {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(getText());
        Clipboard clipboard = Clipboard.getSystemClipboard();
        clipboard.setContent(clipboardContent);
    }

    public void append(String text) {
        boolean needRun = false;
        synchronized (syncObject) {
            if (appendString.length() > MAX_LENGTH) {
                appendString = "<logs buffer overflow>\n".concat(text);
            } else {
                appendString = appendString.concat(text);
            }
            if (!isOutputRunned) {
                needRun = true;
                isOutputRunned = true;
            }
        }
        if (needRun) {
            ContextHelper.runInFxThreadStatic(() -> {
                synchronized (syncObject) {
                    if (output.lengthProperty().get() > MAX_LENGTH) {
                        output.deleteText(0, REMOVE_LENGTH);
                    }
                    output.appendText(appendString);
                    appendString = "";
                    isOutputRunned = false;
                }
            });
        }
    }

    @Override
    public void onNext(byte[] buf, int offset, int length) {
        append(new String(buf, offset, length));
    }
}