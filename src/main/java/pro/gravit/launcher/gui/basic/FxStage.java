package pro.gravit.launcher.gui.basic;

import javafx.scene.Scene;
import javafx.stage.Stage;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.List;

public class FxStage {
    private final Stage stage;
    private final Scene scene;
    private final StackLayers root;

    public FxStage(Stage stage) {
        this.stage = stage;
        this.root = new StackLayers(this);
        this.scene = new Scene(root);
        this.stage.setScene(scene);
        addStylesheet("styles/variables.css");
        addStylesheet("styles/global.css");
    }

    public void addStylesheet(String resource) {
        try {
            this.root.getStylesheets().add(FXApplication.getInstance().getResource(resource).toString());
        } catch (IOException e) {
            LogHelper.error(e);
        }
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.close();
    }

    public void hide() {
        stage.setIconified(true);
    }



    public boolean isShowing() {
        return stage.isShowing();
    }

    public void pushLayer(StackLayers.LayerPosition pos, Layer layer) {
        root.pushLayer(pos, layer);
    }

    public Layer getLayer(StackLayers.LayerPosition position) {
        return root.getLayer(position);
    }

    public void pullLayer(Layer layer) {
        root.pullLayer(layer);
    }

    public List<Layer> getLayers() {
        return root.getLayers();
    }
}
