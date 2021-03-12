package pro.gravit.launcher.client.gui.helper;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;

import java.util.Optional;

public class LookupHelper {
    @SuppressWarnings("unchecked")
    public static <T extends Node> T lookup(Node node, String... names) {
        Node current = node;
        if (current == null) {
            throw new NullPointerException();
        }
        for (int i=0;i<names.length;++i) {
            current = current.lookup(names[i]);
            if (current == null) {
                throw new LookupException(names, i);
            }
        }
        return (T) current;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> Optional<T> lookupIfPossible(Node node, String... names) {
        Node current = node;
        if (current == null) {
            return Optional.empty();
        }
        for (String name : names) {
            current = current.lookup(name);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of((T) current);
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

        public LookupException(String[] stackName, int positionFailed) {
            super(buildStack(stackName, positionFailed));
        }

        private static String buildStack(String[] args, int positionFailed) {
            StringBuilder stringBuilder = new StringBuilder("Lookup failed ");
            boolean first = true;
            for (int i=0;i<args.length;++i) {
                if (!first)
                    stringBuilder.append("->");
                stringBuilder.append(args[i]);
                if (i == positionFailed)
                    stringBuilder.append("(E)");
                first = false;
            }
            return stringBuilder.toString();
        }
    }
}
