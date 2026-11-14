package pro.gravit.launcher.gui.overlays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Labeled;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.impl.FxOverlay;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxStage;
import pro.gravit.launcher.gui.core.impl.ContextHelper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ProcessingOverlay extends FxOverlay {

    private static final Logger logger =
            LoggerFactory.getLogger(ProcessingOverlay.class);

    private Labeled description;

    public ProcessingOverlay(JavaFXApplication application) {
        super("overlay/processing/processing.fxml", application);
    }

    @Override
    public String getName() {
        return "processing";
    }

    @Override
    protected void doInit() {
        description = LookupHelper.lookup(layout, Labeled.class, "#description");
    }

    @Override
    public void reset() {
        description.textProperty().unbind();
        description.getStyleClass().remove("error");
        description.setText("...");
    }

    public void errorHandle(Throwable e) {
        super.errorHandle(e);
        description.textProperty().unbind();
        description.getStyleClass().add("error");
        description.setText(e.toString());
    }

    public final <T> void processRequest(FxStage stage, String message, CompletableFuture<T> request,
            Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        processRequest(stage, message, request, onSuccess, null, onError);
    }

    public final <T> void processRequest(FxStage stage, String message, CompletableFuture<T> request,
            Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        ContextHelper.runInFxThreadStatic(() -> {
            show(stage, null);
            description.setText(message);
            request.whenComplete((result, error) -> ContextHelper.runInFxThreadStatic(() -> {
                hide(0, event -> {
                    try {
                        if (error == null) {
                            if (onSuccess != null) onSuccess.accept(result);
                        } else if (onException != null) {
                            onException.accept(unwrap(error));
                        } else {
                            errorHandle(unwrap(error));
                            if (onError != null) onError.handle(new ActionEvent());
                        }
                    } catch (Throwable callbackError) {
                        errorHandle(callbackError);
                        if (onError != null) onError.handle(new ActionEvent());
                    }
                });
            }));
        }).whenComplete((ignored, setupError) -> {
            if (setupError == null) return;
            ContextHelper.runInFxThreadStatic(() -> {
                try {
                    if (onException != null) onException.accept(unwrap(setupError));
                    else errorHandle(unwrap(setupError));
                } finally { if (onError != null) onError.handle(new ActionEvent()); }
            });
        });
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException)
                && error.getCause() != null) error = error.getCause();
        return error;
    }
}
