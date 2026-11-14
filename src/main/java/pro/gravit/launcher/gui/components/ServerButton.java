package pro.gravit.launcher.gui.components;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.service.ServerButtonState;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxComponent;
import pro.gravit.launcher.gui.core.utils.JavaFxUtils;

public class ServerButton extends FxComponent {

    private static final Logger logger =
            LoggerFactory.getLogger(ServerButton.class);

    private final ProfileFeatureAPI.ClientProfile profile;
    private final ServerButtonState state;
    private Button saveButton;
    private Button resetButton;
    private Region serverLogo;
    private Button activation;

    protected ServerButton(JavaFXApplication application, ProfileFeatureAPI.ClientProfile profile) {
        super(application.serverButtonStateService.getState(profile).getFxmlPath(), application);
        this.profile = profile;
        this.state = application.serverButtonStateService.getState(profile);
    }

    public static ServerButton createServerButton(JavaFXApplication application, ProfileFeatureAPI.ClientProfile profile) {
        return new ServerButton(application, profile);
    }

    @Override
    public String getName() {
        return "serverButton";
    }

    @Override
    protected void doInit() {
        long startTime = System.nanoTime();
        LookupHelper.lookup(layout, Labeled.class, "#nameServer").textProperty().bind(state.nameProperty());
        LookupHelper.lookup(layout, Labeled.class, "#genreServer").textProperty().bind(state.minecraftVersionProperty());
        this.serverLogo = LookupHelper.lookup(layout, Region.class, "#serverLogo");
        this.serverLogo.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
            Image logo = state.logoProperty().get();
            if (logo == null) {
                return null;
            }
            return new Background(new BackgroundImage(logo, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, new BackgroundSize(0.0, 0.0, true, true, false, true)));
        }, state.logoProperty()));
        JavaFxUtils.setRadius(this.serverLogo, 20.0);
        LookupHelper.lookup(layout, Labeled.class, "#online").textProperty().bind(state.onlineTextProperty());
        saveButton = LookupHelper.lookup(layout, Button.class, "#save");
        resetButton = LookupHelper.lookup(layout, Button.class, "#reset");
        double totalMs = (System.nanoTime() - startTime) / 1_000_000.0;
        logger.debug("ServerButton init profile='{}' uuid={} fxml={} image={} totalMs={}",
                state.getName(), profile.getUUID(), state.getFxmlPath(), state.getImagePath(),
                String.format("%.3f", totalMs));
    }

    @Override
    protected void doPostInit() {

    }

    public void configureMenuMode(EventHandler<ActionEvent> eventHandler) {
        ensureInitialized();
        saveButton.setVisible(false); saveButton.setManaged(false);
        resetButton.setVisible(false); resetButton.setManaged(false);
        if (activation == null) {
            activation = new Button();
            activation.setGraphic(layout);
            activation.getStyleClass().add("server-card");
            activation.accessibleTextProperty().bind(state.nameProperty());
            scope.add(() -> activation.accessibleTextProperty().unbind());
        }
        activation.setOnAction(eventHandler);
    }

    public void configureDetailMode(String saveText, EventHandler<ActionEvent> saveHandler,
            String resetText, EventHandler<ActionEvent> resetHandler) {
        ensureInitialized();
        layout.setOnMouseClicked(null); layout.setOnKeyPressed(null);
        layout.setFocusTraversable(false); layout.setCursor(javafx.scene.Cursor.DEFAULT);
        saveButton.setText(saveText); saveButton.setOnAction(saveHandler);
        saveButton.setVisible(saveHandler != null); saveButton.setManaged(saveHandler != null);
        resetButton.setText(resetText); resetButton.setOnAction(resetHandler);
        resetButton.setVisible(resetHandler != null); resetButton.setManaged(resetHandler != null);
    }

    public void addTo(Pane pane) { addTo(pane, 0); }

    public void addTo(Pane pane, int position) {
        ensureInitialized();
        javafx.scene.Node node = activation == null ? layout : activation;
        node.setUserData(profile.getUUID());
        if (node.getParent() == pane) {
            if (pane.getChildren().indexOf(node) == position) return;
            pane.getChildren().remove(node);
        } else if (node.getParent() != null) throw new IllegalStateException("Server cards belong to a single screen");
        pane.getChildren().add(Math.min(position, pane.getChildren().size()), node);
    }

    private void ensureInitialized() {
        if (!isInit()) {
            try { init(); } catch (Exception e) { throw new IllegalStateException("Unable to load server card", e); }
        }
    }

    private void detachFromParent() {
        if (layout.getParent() != null) throw new IllegalStateException("Server cards belong to a single screen");
    }

    private void resetLayoutState() { }

    @Override
    public void dispose() {
        if (isInit()) {
            layout.lookupAll("*").forEach(node -> {
                if (node instanceof Labeled labeled) labeled.textProperty().unbind();
            });
            serverLogo.backgroundProperty().unbind();
            layout.setOnMouseClicked(null); layout.setOnKeyPressed(null);
        }
        super.dispose();
    }

    @Override
    public void reset() {

    }

    @Override
    public void disable() {

    }

    @Override
    public void enable() {

    }
}
