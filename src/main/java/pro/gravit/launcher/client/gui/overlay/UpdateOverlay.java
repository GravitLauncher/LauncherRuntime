package pro.gravit.launcher.client.gui.overlay;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.shape.Circle;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;

import java.io.IOException;

public class UpdateOverlay extends AbstractOverlay {
    public ProgressBar progressBar;
    public Circle[] phases;
    public Label speed;
    public UpdateOverlay(JavaFXApplication application) throws IOException {
        super("overlay/update/update.fxml", application);
    }

    @Override
    protected void doInit() throws IOException {
        progressBar = (ProgressBar) pane.lookup("#progress");
        phases = new Circle[5];
        for(int i=1;i<=5;++i)
        {
            phases[i-1] = (Circle) pane.lookup("#phase".concat(Integer.toString(i)));
        }
        speed = (Label) pane.lookup("#speed");
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
