package pro.gravit.launcher.client.gui.scenes.debug;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.client.JavaRuntimeModule;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.service.LaunchService;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DebugScene extends AbstractScene implements LaunchService.ClientInstance.ProcessListener {
    private static final long MAX_LENGTH = 1024 * 32;
    private static final int REMOVE_LENGTH = 1024 * 4;
    private LaunchService.ClientInstance clientInstance;
    private TextArea output;

    public DebugScene(JavaFXApplication application) {
        super("scenes/debug/debug.fxml", application);
        this.isResetOnShow = true;
    }

    @Override
    protected void doInit() {
        output = LookupHelper.lookup(layout, "#output");
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#kill").ifPresent((x) -> x.setOnAction((e) -> {
            if(clientInstance != null) clientInstance.kill();
        }));

        LookupHelper.<Label>lookupIfPossible(layout, "#version")
                    .ifPresent((v) -> v.setText(JavaRuntimeModule.getMiniLauncherInfo()));
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#copy").ifPresent((x) -> x.setOnAction((e) -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(output.getText());
            Clipboard clipboard = Clipboard.getSystemClipboard();
            clipboard.setContent(clipboardContent);
        }));
        LookupHelper.<ButtonBase>lookup(header, "#back").setOnAction((e) -> {
            if(clientInstance != null) {
                clientInstance.unregisterListener(this);
            }
            try {
                switchScene(application.gui.serverInfoScene);
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
    }


    @Override
    public void reset() {
        output.clear();
    }

    public void onClientInstance(LaunchService.ClientInstance clientInstance) {
        this.clientInstance = clientInstance;
        this.clientInstance.registerListener(this);
        this.clientInstance.getOnWriteParamsFuture().thenAccept((ok) -> {
            append("[START] Write param successful\n");
        }).exceptionally((e) -> {
            errorHandle(e);
            return null;
        });
        this.clientInstance.start().thenAccept((code) -> {
            append(String.format("[START] Process exit with code %d", code));
        }).exceptionally((e) -> {
            errorHandle(e);
            return null;
        });
    }

    private final Object syncObject = new Object();
    private String appendString = "";
    private boolean isOutputRunned;

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
    public void errorHandle(Throwable e) {
        if (!(e instanceof EOFException)) {
            if (LogHelper.isDebugEnabled()) append(e.toString());
        }
    }

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public void onNext(byte[] buf, int offset, int length) {
        append(new String(buf, offset, length));
    }
}
