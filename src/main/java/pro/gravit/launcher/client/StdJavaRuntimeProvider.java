package pro.gravit.launcher.client;

import javafx.application.Application;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.gui.RuntimeProvider;

public class StdJavaRuntimeProvider implements RuntimeProvider {

    private boolean clientInstance;

    public JavaFXApplication getApplication() {
        return JavaFXApplication.getInstance();
    }

    public boolean isClientInstance() {
        return clientInstance;
    }

    @Override
    public void run(String[] args) throws Exception {
        Application.launch(JavaFXApplication.class, args);
    }

    @Override
    public void preLoad() throws Exception {
    }

    @Override
    public void init(boolean clientInstance) {
        this.clientInstance = clientInstance;
    }
}
