package pro.gravit.launcher.gui.helper;

import javafx.scene.Node;

import java.util.Optional;

public class LookupHelper {
    public static <T extends Node> T lookup(Node root, Class<T> type, String... selectors) {
        return new pro.gravit.launcher.gui.core.ui.ViewNodes(root, describe(root)).require(type, selectors);
    }

    public static <T extends Node> Optional<T> lookupIfPossible(Node root, Class<T> type, String... selectors) {
        return new pro.gravit.launcher.gui.core.ui.ViewNodes(root, describe(root)).optional(type, selectors);
    }

    private static String describe(Node root) {
        for (Node current = root; current != null; current = current.getParent()) {
            Object resource = current.getProperties().get("runtime.fxmlResource");
            if (resource != null) return resource.toString();
        }
        return root == null ? "absent component" : root.getClass().getSimpleName() + "#" + root.getId();
    }

    public static class Point2D {
        public double x;
        public double y;

        public Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

    }

    public static <T extends Node> Point2D getAbsoluteCords(Node child, Node parent) {
        Point2D point2D = new Point2D(0, 0);
        // Node current = child //Crash in runtime
        Node current = child;
        while (current != null && current != parent) {
            point2D.x += current.getLayoutX();
            point2D.y += current.getLayoutY();
            current = current.getParent();
        }
        return point2D;
    }

    public static class LookupException extends RuntimeException {

        public LookupException(String[] stackName, int positionFailed) {
            super(buildStack(stackName, positionFailed));
        }

        private static String buildStack(String[] args, int positionFailed) {
            StringBuilder stringBuilder = new StringBuilder("Lookup failed ");
            boolean first = true;
            for (int i = 0; i < args.length; ++i) {
                if (!first) stringBuilder.append("->");
                stringBuilder.append(args[i]);
                if (i == positionFailed) stringBuilder.append("(E)");
                first = false;
            }
            return stringBuilder.toString();
        }
    }
}
