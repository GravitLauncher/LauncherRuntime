package pro.gravit.launcher.gui.commands.runtime;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.utils.command.Command;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import java.util.ArrayList;
import java.util.List;

public class InfoCommand extends Command {
    private final JavaFXApplication application;

    public InfoCommand(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public String getArgsDescription() {
        return "[]";
    }

    @Override
    public String getUsageDescription() {
        return "show javafx info";
    }

    @Override
    public void invoke(String... args) {
        Platform.runLater(() -> {
            LogHelper.info("OS %s ARCH %s Java %d", JVMHelper.OS_TYPE.name(), JVMHelper.ARCH_TYPE.name(), JVMHelper.JVM_VERSION);
            LogHelper.info("JavaFX version: %s", System.getProperty( "javafx.runtime.version"));
            {
                List<String> supportedFeatures = new ArrayList<>();
                List<String> unsupportedFeatures = new ArrayList<>();
                for (var e : ConditionalFeature.values()) {
                    if (Platform.isSupported(e)) {
                        supportedFeatures.add(e.name());
                    } else {
                        unsupportedFeatures.add(e.name());
                    }
                }
                LogHelper.info("JavaFX supported features: [%s]", String.join(",", supportedFeatures));
                LogHelper.info("JavaFX unsupported features: [%s]", String.join(",", unsupportedFeatures));
            }
            LogHelper.info("Is accessibility active %s", Platform.isAccessibilityActive() ? "true" : "false");
        });
    }
}
