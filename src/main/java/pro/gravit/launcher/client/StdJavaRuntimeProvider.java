package pro.gravit.launcher.client;

import javafx.application.Application;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.gui.RuntimeProvider;

public class StdJavaRuntimeProvider implements RuntimeProvider {

    public JavaFXApplication getApplication() {
        return JavaFXApplication.getInstance();
    }

    @Override
    public void run(String[] args) {
        Application.launch(JavaFXApplication.class, args);
    }

    @Override
    public void preLoad() {
    }

    @Override
    public void init(boolean clientInstance) {
    }
}
