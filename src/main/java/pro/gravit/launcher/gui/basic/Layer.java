package pro.gravit.launcher.gui.basic;

import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public abstract class Layer {
    private boolean initialized;
    Pane root;
    StackLayers.LayerPosition layerPosition;
    protected FxStage stage;

    public Layer() {
        var annotation = getClass().getAnnotation(ResourcePath.class);
        if(annotation == null) {
            this.root = new AnchorPane();
        } else {
            try {
                this.root = FXApplication.getInstance().createFxmlLoader(annotation.value()).load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Layer(Pane root) {
        this.root = root;
    }

    public Pane getRoot() {
        return root;
    }

    public final void init() {
        if(!initialized) {
            doInit();
            initialized = true;
        }
    }

    protected abstract void doInit();

    public String getName() {
        return null;
    }

    public void onParent(Layer parent) {

    }

    public void onScene(FxStage stage, StackLayers.LayerPosition position) {

    }

    public void onDetach() {

    }

    protected<T extends Layer> T use(Pane parent, Function<Pane, T> factory) {
        var layer = factory.apply(parent);
        layer.stage = stage;
        layer.onParent(this);
        layer.init();
        return layer;
    }

    protected void inject(Pane content, Layer layer) {
        content.getChildren().add(layer.getRoot());
        layer.stage = stage;
        layer.onParent(this);
        layer.init();
    }

    protected<T extends Node> T lookup(String name) {
        return lookup(root, name);
    }

    protected<T extends Node> Optional<T> lookupIfPossible(String name) {
        return lookupIfPossible(root, name);
    }

    @SuppressWarnings("unchecked")
    protected<T extends Node> T lookup(Pane parent, String name) {
        var t = (T) parent.lookup(name);
        if(t == null) {
            throw new RuntimeException(String.format("Lookup failed: %s not found in %s", name, getName()));
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    protected<T extends Node> Optional<T> lookupIfPossible(Pane parent, String name) {
        var t = (T) parent.lookup(name);
        return Optional.ofNullable(t);
    }

    public static class EmtryLayer extends Layer {

        @Override
        protected void doInit() {

        }
    }
}
