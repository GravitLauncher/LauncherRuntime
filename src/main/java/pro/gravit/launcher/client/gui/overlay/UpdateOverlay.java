package pro.gravit.launcher.client.gui.overlay;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;

import java.io.IOException;

public class UpdateOverlay extends AbstractOverlay {
    public UpdateOverlay(JavaFXApplication application) throws IOException {
        super("overlay/update/update.fxml", application);
    }

    @Override
    protected void doInit() throws IOException {

    }

    @Override
    public void reset() {

    }

    @Override
    public void errorHandle(String error) {

    }

    @Override
    public void errorHandle(Throwable e) {

    }
}
