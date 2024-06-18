package pro.gravit.launcher.gui.basic;

public class LayerPositions {
    public static final StackLayers.LayerPosition SCENE = new StackLayers.LayerPosition();
    public static final StackLayers.LayerPosition OVERLAY = new StackLayers.LayerPosition(SCENE, StackLayers.LayerInsertType.AFTER);
    public static final StackLayers.LayerPosition OVERLAY_PROTECT = new StackLayers.LayerPosition(OVERLAY, StackLayers.LayerInsertType.BEFORE);
}
