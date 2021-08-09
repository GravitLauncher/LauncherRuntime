package pro.gravit.launcher.client.gui.scenes.debug;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.console.ConsoleScene;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DebugScene extends AbstractScene {
    private static final long MAX_LENGTH = 163840;
    private static final int REMOVE_LENGTH = 1024;
    public Process currentProcess;
    public Thread writeParametersThread;
    private Thread readThread;
    private TextArea output;

    public DebugScene(JavaFXApplication application) {
        super("scenes/debug/debug.fxml", application);
    }

    @Override
    protected void doInit() {
        output = LookupHelper.lookup(layout, "#output");
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#kill").ifPresent((x) -> x.setOnAction((e) -> {
            if (currentProcess != null && currentProcess.isAlive())
                currentProcess.destroyForcibly();
        }));

        LookupHelper.<Label>lookupIfPossible(layout, "#version").ifPresent((v) -> v.setText(ConsoleScene.getMiniLauncherInfo()));
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#copy").ifPresent((x) -> x.setOnAction((e) -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(output.getText());
            Clipboard clipboard = Clipboard.getSystemClipboard();
            clipboard.setContent(clipboardContent);
        }));
        LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#hastebin").ifPresent((x) -> x.setOnAction((e) -> {
            String haste = null;
            try {
                haste = hastebin(output.getText());
            } catch (IOException ex) {
                application.messageManager.createNotification(application.getTranslation("runtime.overlay.debug.hastebin.fail.header"),
                        application.getTranslation("runtime.overlay.debug.hastebin.fail.description"));
                LogHelper.error(ex);
            }

            if(haste == null)
                return;

            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(haste);
            Clipboard clipboard = Clipboard.getSystemClipboard();
            clipboard.setContent(clipboardContent);

            application.openURL(haste);
        }));
        LookupHelper.<ButtonBase>lookup(header, "#controls", "#back").setOnAction((e) -> {
            if (writeParametersThread != null && writeParametersThread.isAlive())
            {
                if(currentProcess.isAlive()) writeParametersThread.interrupt();
            }
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
            if (LogHelper.isDebugEnabled())
                append(e.toString());
        }
        if (currentProcess != null && !currentProcess.isAlive()) {
            onProcessExit(currentProcess.exitValue());
        }
    }

    @Override
    public String getName() {
        return "debug";
    }

    private void onProcessExit(int code) {
        append(String.format("Process exit code %d", code));
        if (writeParametersThread != null) writeParametersThread.interrupt();
    }

    public static class HasteResponse {
        @LauncherNetworkAPI
        public String key;
    }

    public String hastebin(String log) throws IOException {
        if(application.guiModuleConfig.hastebinServer == null)
            throw new NullPointerException("Regenerate the config \"JavaRuntime.json\"");
        URL url = new URL(application.guiModuleConfig.hastebinServer + "/documents");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(log);
            writer.flush();
        }

        InputStreamReader reader;
        int statusCode = connection.getResponseCode();

        if (200 <= statusCode && statusCode < 300)
            reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        else
            reader = new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8);
        try {
            HasteResponse obj = Launcher.gsonManager.gson.fromJson(reader, HasteResponse.class);
            application.messageManager.createNotification(application.getTranslation("runtime.overlay.debug.hastebin.success.header"),
                    application.getTranslation("runtime.overlay.debug.hastebin.success.description"));
            return application.guiModuleConfig.hastebinServer + "/" + obj.key;
        } catch (Exception e) {
            if (200 > statusCode || statusCode > 300) {
                application.messageManager.createNotification(application.getTranslation("runtime.overlay.debug.hastebin.fail.header"),
                        application.getTranslation("runtime.overlay.debug.hastebin.fail.description"));
                LogHelper.error("JsonRequest failed. Server response code %d", statusCode);
                throw new IOException(e);
            }
            return null;
        }
    }

}
