package pro.gravit.launcher.gui.overlays;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Labeled;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.impl.AbstractStage;
import pro.gravit.launcher.gui.impl.ContextHelper;
import pro.gravit.launcher.base.request.Request;
import pro.gravit.launcher.base.request.WebSocketEvent;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.function.Consumer;

public class ProcessingOverlay extends AbstractOverlay {
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
        // spinner = LookupHelper.lookup(pane, "#spinner"); //TODO: DrLeonardo?
        description = LookupHelper.lookup(layout, "#description");
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

    public final <T extends WebSocketEvent> void processRequest(AbstractStage stage, String message, Request<T> request,
            Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        processRequest(stage, message, request, onSuccess, null, onError);
    }

    public final <T extends WebSocketEvent> void processRequest(AbstractStage stage, String message, Request<T> request,
            Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        try {
            ContextHelper.runInFxThreadStatic(() -> show(stage, (e) -> {
                try {
                    description.setText(message);
                    application.service.request(request).thenAccept((result) -> {
                        LogHelper.dev("RequestFuture complete normally");
                        onSuccess.accept(result);
                        ContextHelper.runInFxThreadStatic(() -> hide(0, null));
                    }).exceptionally((error) -> {
                        if (onException != null) onException.accept(error);
                        else ContextHelper.runInFxThreadStatic(() -> errorHandle(error.getCause()));
                        ContextHelper.runInFxThreadStatic(() -> hide(2500, onError));
                        return null;
                    });
                } catch (IOException ex) {
                    errorHandle(ex);
                    hide(2500, onError);
                }
            }));
        } catch (Exception e) {
            ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
        }
    }
}
