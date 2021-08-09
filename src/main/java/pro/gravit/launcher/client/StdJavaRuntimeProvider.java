package pro.gravit.launcher.client;

import javafx.application.Application;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.gui.RuntimeProvider;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class StdJavaRuntimeProvider implements RuntimeProvider {
    public static Path launcherUpdateTempPath;
    public static ProcessBuilder processBuilder;
    private static final AtomicReference<StdJavaRuntimeProvider> INSTANCE = new AtomicReference<>();

    public StdJavaRuntimeProvider() {
        INSTANCE.set(this);
    }

    public static StdJavaRuntimeProvider getInstance() {
        return INSTANCE.get();
    }

    public JavaFXApplication getApplication() {
        return JavaFXApplication.getInstance();
    }

    @Override
    public void run(String[] args) {
        Application.launch(JavaFXApplication.class, args);
        LogHelper.debug("Post Application.launch method invoked");
        if(launcherUpdateTempPath != null && processBuilder != null)
        {
            try {
                Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
                try (InputStream in = IOHelper.newInput(launcherUpdateTempPath)) {
                    IOHelper.transfer(in, BINARY_PATH);
                }
                Files.deleteIfExists(launcherUpdateTempPath);
                processBuilder.start();
            } catch (Throwable e)
            {
                LogHelper.error(e);
            }

        }
        System.exit(0);
    }

    @Override
    public void preLoad() {
    }

    protected void registerPrivateCommands() {
        JavaFXApplication application = JavaFXApplication.getInstance();
        if(application != null) {
            application.registerPrivateCommands();
        }
    }

    @Override
    public void init(boolean clientInstance) {
        if(clientInstance) {
            JavaFXApplication.setNoGUIMode(true);
        }
    }
}
