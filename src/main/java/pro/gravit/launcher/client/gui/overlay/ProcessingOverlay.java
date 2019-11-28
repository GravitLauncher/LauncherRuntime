package pro.gravit.launcher.client.gui.overlay;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.interfaces.FXMLConsumer;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.function.Consumer;

public class ProcessingOverlay extends AbstractOverlay implements FXMLConsumer {
    private Node spinner;
    private Labeled description;
    public ProcessingOverlay(JavaFXApplication application) throws IOException {
        super("overlay/processing/processing.fxml", application);
    }

    @Override
    protected void doInit() throws IOException {
        spinner = pane.lookup("#spinner");
        description = (Labeled) pane.lookup("#description");
    }

    @Override
    public void reset() {
        description.textProperty().unbind();
        description.getStyleClass().remove("error");
        description.setText("...");
    }
    public void errorHandle(String e)
    {
        LogHelper.error(e);
        description.textProperty().unbind();
        description.getStyleClass().add("error");
        description.setText(e);
    }
    public void errorHandle(Throwable e)
    {
        LogHelper.error(e);
        description.textProperty().unbind();
        description.getStyleClass().add("error");
        description.setText(e.toString());
    }
    public final<T extends WebSocketEvent> void processRequest(AbstractScene scene, ObservableValue<String> message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        scene.showOverlay(this, (e) -> {
            try {
                description.textProperty().bind(message);
                application.requestHandler.request(request).thenAccept((result) -> {
                    LogHelper.dev("RequestFuture complete normally");
                    onSuccess.accept(result);
                }).exceptionally((error) -> {
                    ContextHelper.runInFxThreadStatic(() -> errorHandle(error.getCause()));
                    hide(2500, scene, onError);
                    return null;
                });
            } catch (IOException ex) {
                errorHandle(ex);
                hide(2500, scene, onError);
            }
        });
    }
    public final<T extends WebSocketEvent> void processRequest(AbstractScene scene, String message, Request<T> request, Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        scene.showOverlay(this, (e) -> {
            try {
                description.setText(message);
                application.requestHandler.request(request).thenAccept((result) -> {
                    LogHelper.dev("RequestFuture complete normally");
                    onSuccess.accept(result);
                }).exceptionally((error) -> {
                    ContextHelper.runInFxThreadStatic(() -> errorHandle(error.getCause()));
                    hide(2500, scene, onError);
                    return null;
                });
            } catch (IOException ex) {
                errorHandle(ex);
                hide(2500, scene, onError);
            }
        });
    }

    @Override
    public String getFxmlPath() {
        return name;
    }
}
