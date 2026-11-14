package pro.gravit.launcher.gui.core.ui;

import javafx.beans.property.*;
import javafx.scene.control.ButtonBase;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class UiAction {
    private final StringProperty text = new SimpleStringProperty("");
    private final BooleanProperty available = new SimpleBooleanProperty(true);
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper();
    private final Supplier<? extends CompletionStage<?>> operation;
    private final Consumer<Throwable> onError;

    public UiAction(String text, Supplier<? extends CompletionStage<?>> operation, Consumer<Throwable> onError) {
        this.text.set(text);
        this.operation = operation;
        this.onError = onError;
    }

    public StringProperty textProperty() { return text; }
    public BooleanProperty availableProperty() { return available; }
    public ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }

    public void execute() {
        FxDispatcher.checkThread();
        if (!available.get() || running.get()) return;
        running.set(true);
        try {
            operation.get().whenComplete((value, error) -> FxDispatcher.INSTANCE.execute(() -> {
                running.set(false);
                if (error != null) onError.accept(error);
            }));
        } catch (Throwable e) { running.set(false); onError.accept(e); }
    }

    public void attach(UiScope scope, ButtonBase button) {
        button.textProperty().bind(text);
        button.disableProperty().bind(available.not().or(running));
        javafx.event.EventHandler<javafx.event.ActionEvent> handler = event -> execute();
        button.addEventHandler(javafx.event.ActionEvent.ACTION, handler);
        scope.add(() -> {
            button.textProperty().unbind(); button.disableProperty().unbind();
            button.removeEventHandler(javafx.event.ActionEvent.ACTION, handler);
        });
    }
}
