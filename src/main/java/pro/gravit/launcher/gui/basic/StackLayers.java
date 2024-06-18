package pro.gravit.launcher.gui.basic;

import javafx.scene.layout.StackPane;

import java.util.*;

public class StackLayers extends StackPane {
    private final List<Layer> layers = new ArrayList<>();
    private final Map<LayerPosition, Layer> positionLayerMap = new HashMap<>();
    private final FxStage stage;
    public StackLayers(FxStage stage) {
        this.stage = stage;
    }

    public void pushLayer(LayerPosition pos, Layer layer) {
        Layer before = getLayer(pos);
        if(before != null) {
            before.onDetach();
            before.stage = null;
            layers.replaceAll(e -> {
                if(e == before) {
                    return layer;
                }
                return before;
            });
            getChildren().replaceAll(e -> {
                if(e == before.getRoot()) {
                    return layer.getRoot();
                }
                return e;
            });
            positionLayerMap.put(pos, layer);
        } else if(pos.reference == null) {
            layers.add(layer);
            positionLayerMap.put(pos, layer);
            getChildren().add(layer.getRoot());
        } else {
            Layer ref;
            LayerPosition refPos = pos.reference;
            LayerInsertType refType;
            do {
                if(refPos == null) {
                    throw new RuntimeException("Can't find correct position");
                }
                refType = refPos.type;
                ref = getLayer(refPos);
                refPos = refPos.reference;
            } while (ref == null);
            int index = layers.indexOf(ref);
            var invertedType = invertType(refType);
            if(refType == LayerInsertType.AFTER) {
                for(var i = index;i < layers.size();++i) {
                    var l = layers.get(i);
                    if(refPos.equals(l.layerPosition.reference)) {
                        if(l.layerPosition.type == invertedType) {
                            refType = invertedType;
                            index = i;
                            break;
                        }
                    }
                }
            } else {
                for(var i = index;i >= 0;--i) {
                    var l = layers.get(i);
                    if(refPos.equals(l.layerPosition.reference)) {
                        if(l.layerPosition.type == invertedType) {
                            refType = invertedType;
                            index = i;
                            break;
                        }
                    }
                }
            }
            layers.add(index+refType.offset, layer);
            positionLayerMap.put(pos, layer);
            layer.layerPosition = pos;
            getChildren().add(index+refType.offset, layer.getRoot());
        }
        layer.stage = stage;
        layer.onScene(stage, pos);
        layer.init();
    }

    @Deprecated
    public void pushLayer(LayerPosition pos, Layer layer, LayerPosition reference, LayerInsertType type) {
        if(reference == null) {
            layers.add(layer);
            positionLayerMap.put(pos, layer);
            getChildren().add(layer.getRoot());
        } else {
            Layer ref = getLayer(reference);
            if(ref == null) {
                throw new RuntimeException("Refrernce layer not found");
            }
            int index = getChildren().indexOf(ref.getRoot());
            layers.add(index+type.offset, layer);
            positionLayerMap.put(pos, layer);
            getChildren().add(index+type.offset, layer.getRoot());
        }
    }

    public Layer getLayer(LayerPosition position) {
        return positionLayerMap.get(position);
    }

    public void pullLayer(Layer layer) {
        layers.remove(layer);
        getChildren().remove(layer.getRoot());
    }

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    private LayerInsertType invertType(LayerInsertType type) {
        if(type == LayerInsertType.BEFORE) {
            return LayerInsertType.AFTER;
        }
        return LayerInsertType.BEFORE;
    }

    public static class LayerPosition {
        public LayerPosition reference;
        public LayerInsertType type;

        public LayerPosition() {
        }

        public LayerPosition(LayerPosition reference, LayerInsertType type) {
            this.reference = reference;
            this.type = type;
        }
    }

    public enum LayerInsertType {
        BEFORE(0), AFTER(1);

        public final int offset;

        LayerInsertType(int offset) {
            this.offset = offset;
        }
    }
}
