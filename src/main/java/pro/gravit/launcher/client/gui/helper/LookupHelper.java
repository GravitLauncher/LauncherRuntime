package pro.gravit.launcher.client.gui.helper;

import javafx.scene.Node;
import javafx.scene.Parent;

public class LookupHelper {
    @SuppressWarnings("unchecked")
    public static <T extends Node> T lookup(Node node, String... names) {
        Node current = node;
        if (current == null) {
            throw new NullPointerException();
        }
        for (String name : names) {
            current = current.lookup(name);
            if (current == null) {
                throw new LookupException(names, name);
            }
        }
        return (T) current;
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
        Point2D point2D = new Point2D(0,0);
        // Node current = child //Crash in runtime
        Parent current = (Parent) child;
        while(current != parent) {
            point2D.x += current.getLayoutX();
            point2D.y += current.getLayoutY();
            current = current.getParent();
            if(current == null) break;
        }
        return point2D;
    }

    public static class LookupException extends RuntimeException {

        public LookupException(String[] stackName, String failName) {
            super(buildStack(stackName, failName));
        }

        private static String buildStack(String[] args, String failed) {
            StringBuilder stringBuilder = new StringBuilder("Lookup failed ");
            boolean first = true;
            for (String argument : args) {
                if (!first)
                    stringBuilder.append("->");
                stringBuilder.append(argument);
                if (!argument.equals(failed))
                    stringBuilder.append("(E)");
                first = false;
            }
            return stringBuilder.toString();
        }
    }
}
