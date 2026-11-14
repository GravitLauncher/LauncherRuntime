package pro.gravit.launcher.gui.core.ui;

import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewNodesTest {
    @Test
    void resolvesTypedNodesAndReportsMissingSelector() {
        Pane root = new Pane();
        Region region = new Region();
        region.setId("panel");
        root.getChildren().add(region);
        ViewNodes nodes = new ViewNodes(root, "runtime/example.fxml");

        assertEquals(region, nodes.require(Region.class, "#panel"));
        assertTrue(nodes.optional(Region.class, "#absent").isEmpty());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> nodes.require(Region.class, "#absent"));
        assertTrue(error.getMessage().contains("runtime/example.fxml"));
        assertTrue(error.getMessage().contains("#absent"));
    }

    @Test
    void reportsTypeMismatchWithExpectedAndActualTypes() {
        Pane root = new Pane();
        Region region = new Region();
        region.setId("panel");
        root.getChildren().add(region);
        ViewNodes nodes = new ViewNodes(root, "runtime/example.fxml");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> nodes.require(Pane.class, "#panel"));
        assertInstanceOf(IllegalArgumentException.class, error);
        assertTrue(error.getMessage().contains("expected Pane"));
        assertTrue(error.getMessage().contains("found Region"));
    }
}
