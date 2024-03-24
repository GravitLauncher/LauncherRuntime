package pro.gravit.launcher.gui.scenes.console;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.runtime.managers.ConsoleManager;
import pro.gravit.utils.helper.LogHelper;

public class ConsoleScene extends AbstractScene {
    private static final long MAX_LENGTH = 16384;
    private static final int REMOVE_LENGTH = 1024;
    private TextField commandLine;
    private TextArea output;

    public ConsoleScene(JavaFXApplication application) {
        super("scenes/console/console.fxml", application);
    }

    @Override
    protected void doInit() {
        output = LookupHelper.lookup(layout, "#output");
        commandLine = LookupHelper.lookup(layout, "#commandInput");
        LogHelper.addOutput(this::append, LogHelper.OutputTypes.PLAIN);
        commandLine.setOnAction(this::send);
        LookupHelper.<ButtonBase>lookup(layout, "#send").setOnAction(this::send);
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
            ConsoleManager.handler.evalNative(command, false);
            commandLine.getStyleClass().removeAll("InputError");
        } catch (Exception ex) {
            LogHelper.error(ex);
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
