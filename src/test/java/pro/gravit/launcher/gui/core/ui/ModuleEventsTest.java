package pro.gravit.launcher.gui.core.ui;

import org.junit.jupiter.api.Test;
import pro.gravit.launcher.base.modules.EventBus;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ModuleEventsTest {
    @Test void subscriptionsHaveScopeAndListenerFailuresDoNotEscapeTheBus() throws Exception {
        EventBus bus = new EventBus();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        UiScope scope = new UiScope();
        FxTestThread.run(() -> ModuleEvents.listen(scope, bus, String.class, event -> {
            calls.incrementAndGet();
            throw new IllegalStateException("expected failure");
        }, error -> failures.incrementAndGet()));
        bus.publish("event");
        FxTestThread.run(() -> {
            assertEquals(1, calls.get());
            assertEquals(1, failures.get());
            scope.close();
        });
        bus.publish("ignored");
        FxTestThread.run(() -> assertEquals(1, calls.get()));
    }
}
