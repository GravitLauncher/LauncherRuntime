package pro.gravit.launcher.client.gui.scene;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.managers.ConsoleManager;
import pro.gravit.utils.Version;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

public class ConsoleScene extends AbstractScene {
    public static final long MAX_LENGTH = 16384;
    public static final int REMOVE_LENGTH = 1024;
    public Node layout;
    public TextField commandLine;
    public TextArea output;

    public ConsoleScene(JavaFXApplication application) {
        super("scenes/console/console.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        layout = scene.getRoot();
        sceneBaseInit(layout);
        output = (TextArea) layout.lookup("#output");
        commandLine = (TextField) layout.lookup("#commandInput");
        LogHelper.addOutput(this::append, LogHelper.OutputTypes.PLAIN);
        commandLine.setOnAction(this::send);
        ((ButtonBase) layout.lookup("#send")).setOnAction(this::send);
        ((Labeled) layout.lookup("#version")).setText(getMiniLauncherInfo());
    }

    public void send(ActionEvent ignored) {
        String command = commandLine.getText();
        commandLine.clear();
        try {
            ConsoleManager.handler.evalNative(command, false);
            commandLine.getStyleClass().remove("InputError");
        } catch (Exception ex) {
            LogHelper.error(ex);
            commandLine.getStyleClass().add("InputError");
        }
    }

    public void append(String text) {
        contextHelper.runInFxThread(() -> {
            if (output.lengthProperty().get() > MAX_LENGTH)
                output.deleteText(0, REMOVE_LENGTH);
            output.appendText(text.concat("\n"));
        });
    }

    public static String getLauncherInfo() {
        return String.format("Launcher %s | Java %d(%s %s) x%d | %s x%d", Version.getVersion().toString(), JVMHelper.JVM_VERSION, JVMHelper.RUNTIME_MXBEAN.getVmName(), System.getProperty("java.version"), JVMHelper.JVM_BITS,
                JVMHelper.OS_TYPE.name(), JVMHelper.OS_BITS);
    }

    public static String getMiniLauncherInfo() {
        return String.format("Launcher %s | Java %d(%s) x%d | %s x%d", Version.getVersion().toString(), JVMHelper.JVM_VERSION, System.getProperty("java.version"), JVMHelper.JVM_BITS,
                JVMHelper.OS_TYPE.name(), JVMHelper.OS_BITS);
    }
}
