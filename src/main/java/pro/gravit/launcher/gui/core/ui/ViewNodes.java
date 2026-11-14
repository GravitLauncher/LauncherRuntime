package pro.gravit.launcher.gui.core.ui;

import javafx.scene.Node;
import java.util.Optional;

/** Resolves nodes once, within the owning FXML component's root. */
public record ViewNodes(Node root, String resource) {
    public <T extends Node> T require(Class<T> type, String... selectors) {
        return optional(type, selectors).orElseThrow(() -> new IllegalArgumentException(
                resource + ": missing " + String.join(" -> ", selectors) + " (expected " + type.getSimpleName() + ")"));
    }

    public <T extends Node> Optional<T> optional(Class<T> type, String... selectors) {
        Node current = root;
        for (String selector : selectors) {
            if (current == null) return Optional.empty();
            current = current.lookup(selector);
        }
        if (current == null) return Optional.empty();
        if (!type.isInstance(current)) throw new IllegalArgumentException(resource + ": "
                + String.join(" -> ", selectors) + " expected " + type.getSimpleName()
                + ", found " + current.getClass().getSimpleName());
        return Optional.of(type.cast(current));
    }
}
