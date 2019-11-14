package pro.gravit.launcher.client.gui.helper;

import javafx.scene.Node;

public class LookupHelper {
    @SuppressWarnings("unchecked")
    public static<T extends Node> T lookup(Node node, String... names)
    {
        Node current = node;
        if(current == null)
        {
            throw new NullPointerException();
        }
        for(String s : names)
        {
            current = current.lookup(s);
            if(current == null)
            {
                throw new LookupException(names, s);
            }
        }
        return (T) current;
    }
    public static class LookupException extends RuntimeException
    {
        public LookupException() {
        }

        public LookupException(String[] stackName, String failName) {
            super(buildStack(stackName, failName));
        }
        private static String buildStack(String[] args, String failed) {
            StringBuilder b = new StringBuilder("Lookup failed ");
            boolean f = true;
            for (String s : args)
            {
                if(!f)
                    b.append("->");
                b.append(s);
                if(!s.equals(failed))
                    b.append("(E)");
                f = false;
            }
            return b.toString();
        }

        public LookupException(String message) {
            super(message);
        }

        public LookupException(String message, Throwable cause) {
            super(message, cause);
        }

        public LookupException(Throwable cause) {
            super(cause);
        }

        public LookupException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
