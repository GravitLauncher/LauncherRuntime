package pro.gravit.launcher.client.gui.overlay;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.scene.AbstractScene;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ProcessingOverlay extends AbstractOverlay {
    private Node spinner;
    private Labeled description;
    public ProcessingOverlay(JavaFXApplication application) throws IOException {
        super("dialog/overlay/processing/processing.fxml", application);
    }

    @Override
    protected void doInit() throws IOException {
        spinner = pane.lookup("#spinner");
        description = (Labeled) pane.lookup("#description");
    }

    @Override
    public void reset() {
        description.getStyleClass().remove("error");
        description.setText("...");
    }
    public void setError(String e)
    {
        LogHelper.error(e);
        description.textProperty().unbind();
        description.getStyleClass().add("error");
        description.setText(e);
    }
    public void setError(Throwable e)
    {
        LogHelper.error(e);
        description.textProperty().unbind();
        description.getStyleClass().add("error");
        description.setText(e.toString());
    }
    public final<T extends WebSocketEvent> void processRequest(ObservableValue<String> message, AbstractScene scene, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        try {
            show(scene, (e) -> {
                try {
                    description.textProperty().bind(message);
                    application.requestHandler.request(request).thenAccept((result) -> {
                        LogHelper.dev("RequestFuture complete normally");
                        onSuccess.accept(result);
                    }).exceptionally((error) -> {
                        hide(2500, scene, onError);
                        return null;
                    });
                } catch (IOException ex) {
                    setError(ex);
                    hide(2500, scene, onError);
                }
            });
        } catch (IOException | InterruptedException e) {
            setError(e);
            hide(2500, scene, onError);
        }
    }
    private static class ObservableStaticString implements ObservableValue<String>
    {
        public final String value;

        private ObservableStaticString(String value) {
            this.value = value;
        }

        @Override
        public void addListener(ChangeListener<? super String> changeListener) {
            // None
        }

        @Override
        public void removeListener(ChangeListener<? super String> changeListener) {
            // None
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public void addListener(InvalidationListener invalidationListener) {
            // None
        }

        @Override
        public void removeListener(InvalidationListener invalidationListener) {
            // None
        }
    }
    public final<T extends WebSocketEvent> void processRequest(String message, AbstractScene scene, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        ObservableValue<String> value = new ObservableStaticString(message);
        processRequest(value, scene,request, onSuccess, onError);
    }
}
