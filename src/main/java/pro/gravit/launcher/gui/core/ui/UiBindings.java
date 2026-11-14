package pro.gravit.launcher.gui.core.ui;

import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;

public final class UiBindings {
    private UiBindings() { }

    public static void visibility(UiScope scope, Node node, ObservableValue<Boolean> visible) {
        node.visibleProperty().bind(visible);
        node.managedProperty().bind(visible);
        scope.add(() -> { node.visibleProperty().unbind(); node.managedProperty().unbind(); });
    }

    public static void pseudoClass(UiScope scope, Node node, String name, ObservableValue<Boolean> active) {
        PseudoClass state = PseudoClass.getPseudoClass(name);
        scope.subscribe(active.subscribe(value -> node.pseudoClassStateChanged(state, Boolean.TRUE.equals(value))));
        scope.add(() -> node.pseudoClassStateChanged(state, false));
    }
}
