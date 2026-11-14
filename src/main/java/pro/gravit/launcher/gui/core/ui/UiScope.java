package pro.gravit.launcher.gui.core.ui;

import javafx.util.Subscription;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Owns cleanup for a view or a single period of visibility. */
public final class UiScope implements AutoCloseable {
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;

    public void add(Runnable action) {
        Objects.requireNonNull(action);
        if (closed) action.run();
        else cleanup.push(action);
    }

    public void subscribe(Subscription subscription) {
        add(subscription::unsubscribe);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        while (!cleanup.isEmpty()) {
            try { cleanup.pop().run(); }
            catch (RuntimeException | Error e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }
}
