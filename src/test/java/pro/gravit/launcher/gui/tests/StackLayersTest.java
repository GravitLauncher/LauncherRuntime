package pro.gravit.launcher.gui.tests;

import javafx.scene.layout.AnchorPane;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.launcher.gui.basic.StackLayers;

import java.util.List;

public class StackLayersTest {
    @Test
    public void basicTest() {
        StackLayers layers = new StackLayers(null);
        StackLayers.LayerPosition main = new StackLayers.LayerPosition();
        StackLayers.LayerPosition overlay = new StackLayers.LayerPosition();
        StackLayers.LayerPosition beforeOverlay = new StackLayers.LayerPosition();
        {
            Layer layerMain = new Layer.EmtryLayer();
            layers.pushLayer(main, layerMain, null, null);
            Assertions.assertIterableEquals(layers.getLayers(), List.of(layerMain));

            Layer overlayMain = new Layer.EmtryLayer();
            layers.pushLayer(overlay, overlayMain, main, StackLayers.LayerInsertType.AFTER);
            Assertions.assertIterableEquals(layers.getLayers(), List.of(layerMain, overlayMain));

            Layer beforeOverlayMain = new Layer.EmtryLayer();
            layers.pushLayer(beforeOverlay, beforeOverlayMain, overlay, StackLayers.LayerInsertType.BEFORE);
            Assertions.assertIterableEquals(layers.getLayers(), List.of(layerMain, beforeOverlayMain, overlayMain));
        }
    }
}
