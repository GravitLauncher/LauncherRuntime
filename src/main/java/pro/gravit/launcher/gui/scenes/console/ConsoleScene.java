package pro.gravit.launcher.gui.scenes.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxScene;
import pro.gravit.launcher.runtime.managers.ConsoleManager;


public class ConsoleScene extends FxScene {

    private static final Logger logger =
            LoggerFactory.getLogger(ConsoleScene.class);

    private static final long MAX_LENGTH = 16384;
    private static final int REMOVE_LENGTH = 1024;
    private TextField commandLine;
    private TextArea output;
    private java.nio.file.Path logFile;
    private javafx.animation.Timeline refresh;
    private boolean reading;
    private long logVersion;

    @Override public void onShow() {
        super.onShow();
        if (logFile != null) {
            refresh = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> refreshLog()));
            refresh.setCycleCount(javafx.animation.Animation.INDEFINITE);
            refresh.play(); refreshLog();
        }
    }
    @Override public void onHide() {
        logVersion++;
        if (refresh != null) { refresh.stop(); refresh = null; }
        super.onHide();
    }
    private void refreshLog() {
        if (reading) return;
        reading = true;
        long version = logVersion;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try (var input = java.nio.channels.FileChannel.open(logFile)) {
                long start = Math.max(0, input.size() - MAX_LENGTH);
                input.position(start);
                var buffer = java.nio.ByteBuffer.allocate((int) Math.min(MAX_LENGTH, input.size()));
                while (input.read(buffer) > 0) { }
                buffer.flip();
                return java.nio.charset.StandardCharsets.UTF_8.decode(buffer).toString();
            } catch (java.io.IOException error) { return error.getMessage(); }
        }, application.workers).whenComplete((text, error) -> contextHelper.runInFxThread(() -> {
            reading = false;
            if (version == logVersion && error == null && !text.equals(output.getText())) output.setText(text);
        }));
    }

    public ConsoleScene(JavaFXApplication application) {
        super("scenes/console/console.fxml", application);
    }

    @Override
    protected void doInit() {
        output = LookupHelper.lookup(layout, TextArea.class, "#output");
        commandLine = LookupHelper.lookup(layout, TextField.class, "#commandInput");
        String file = System.getProperty("org.slf4j.simpleLogger.logFile", "System.out");
        if (file.equals("System.out") || file.equals("System.err")) {
            output.setText(application.getTranslation("runtime.console.logUnavailable", "Live launcher logs are unavailable. Start with --log-output to view Launcher.log."));
        } else {
            logFile = java.nio.file.Path.of(file);
        }
        commandLine.setOnAction(this::send);
        LookupHelper.lookup(layout, ButtonBase.class, "#send").setOnAction(this::send);
    }

    @Override
    public void reset() {
        output.clear();
        commandLine.clear();
        commandLine.getStyleClass().removeAll("InputError");
    }

    @Override
    public String getName() {
        return "console";
    }

    private void send(ActionEvent ignored) {
        String command = commandLine.getText();
        commandLine.clear();
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { ConsoleManager.handler.evalNative(command, false); }
                catch (Exception error) { contextHelper.runInFxThread(() -> { append(error.getMessage()); commandLine.getStyleClass().add("InputError"); }); }
            }, application.workers);
            commandLine.getStyleClass().removeAll("InputError");
        } catch (Exception ex) {
            logger.error("", ex);
            commandLine.getStyleClass().add("InputError");
        }
    }

    private void append(String text) {
        contextHelper.runInFxThread(() -> {
            if (output.lengthProperty().get() > MAX_LENGTH) output.deleteText(0, REMOVE_LENGTH);
            output.appendText(text.concat("\n"));
        });
    }
}