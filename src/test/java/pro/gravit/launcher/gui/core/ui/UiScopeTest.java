package pro.gravit.launcher.gui.core.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiScopeTest {
    @Test
    void closeRunsEveryCleanupOnceEvenWhenOneFails() {
        UiScope scope = new UiScope();
        List<Integer> calls = new ArrayList<>();
        scope.add(() -> calls.add(1));
        scope.add(() -> { calls.add(2); throw new IllegalStateException("cleanup failed"); });
        scope.add(() -> calls.add(3));

        IllegalStateException error = assertThrows(IllegalStateException.class, scope::close);

        assertEquals("cleanup failed", error.getMessage());
        assertEquals(List.of(3, 2, 1), calls);
        scope.close();
        assertEquals(List.of(3, 2, 1), calls);
    }

    @Test
    void cleanupAddedAfterCloseRunsImmediately() {
        UiScope scope = new UiScope();
        List<String> calls = new ArrayList<>();
        scope.close();
        scope.add(() -> calls.add("late"));
        assertEquals(List.of("late"), calls);
    }
}
