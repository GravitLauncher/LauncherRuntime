package pro.gravit.launcher.client.gui.helper;

import javafx.scene.Node;

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
