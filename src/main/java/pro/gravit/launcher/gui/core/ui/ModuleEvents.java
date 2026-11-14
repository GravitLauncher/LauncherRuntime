package pro.gravit.launcher.gui.core.ui;

import pro.gravit.launcher.base.modules.EventBus;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Adapts module events to the lifetime and thread of a JavaFX view. */
public final class ModuleEvents {
    private ModuleEvents() { }

    public static <T> void listen(UiScope scope, EventBus bus, Class<T> type,
                                  Consumer<T> handler, Consumer<Throwable> onError) {
        AtomicBoolean active = new AtomicBoolean(true);
        var subscription = bus.register(type, event ->
                FxDispatcher.INSTANCE.run(() -> {
                    if (active.get()) handler.accept(event);
                }).exceptionally(error -> { onError.accept(error); return null; }));
        scope.add(() -> { active.set(false); subscription.cancel(); });
    }
}
