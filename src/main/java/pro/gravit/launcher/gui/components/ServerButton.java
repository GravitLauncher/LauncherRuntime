package pro.gravit.launcher.gui.components;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
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
        LookupHelper.<Labeled>lookup(layout, "#nameServer").textProperty().bind(state.nameProperty());
        LookupHelper.<Labeled>lookup(layout, "#genreServer").textProperty().bind(state.minecraftVersionProperty());
        this.serverLogo = LookupHelper.lookup(layout, "#serverLogo");
        this.serverLogo.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
            Image logo = state.logoProperty().get();
            if (logo == null) {
                return null;
            }
            return new Background(new BackgroundImage(logo, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, new BackgroundSize(0.0, 0.0, true, true, false, true)));
        }, state.logoProperty()));
        JavaFxUtils.setRadius(this.serverLogo, 20.0);
        LookupHelper.<Labeled>lookup(layout, "#online").textProperty().bind(state.onlineTextProperty());
        saveButton = LookupHelper.lookup(layout, "#save");
        resetButton = LookupHelper.lookup(layout, "#reset");
        layout.cursorProperty().bind(state.cursorProperty());
        layout.setOnMouseClicked(state.mouseClickedHandlerProperty().get());
        state.mouseClickedHandlerProperty().addListener((observable, oldValue, newValue) -> layout.setOnMouseClicked(newValue));
        saveButton.visibleProperty().bind(state.saveVisibleProperty());
        saveButton.managedProperty().bind(state.saveVisibleProperty());
        saveButton.textProperty().bind(state.saveTextProperty());
        saveButton.setOnAction(state.saveActionProperty().get());
        state.saveActionProperty().addListener((observable, oldValue, newValue) -> saveButton.setOnAction(newValue));
        resetButton.visibleProperty().bind(state.resetVisibleProperty());
        resetButton.managedProperty().bind(state.resetVisibleProperty());
        resetButton.textProperty().bind(state.resetTextProperty());
        resetButton.setOnAction(state.resetActionProperty().get());
        state.resetActionProperty().addListener((observable, oldValue, newValue) -> resetButton.setOnAction(newValue));
        double totalMs = (System.nanoTime() - startTime) / 1_000_000.0;
        logger.debug("ServerButton init profile='{}' uuid={} fxml={} image={} totalMs={}",
                state.getName(), profile.getUUID(), state.getFxmlPath(), state.getImagePath(),
                String.format("%.3f", totalMs));
    }

    @Override
    protected void doPostInit() {

    }

    public void configureMenuMode(EventHandler<MouseEvent> eventHandler) {
        state.configureMenuMode(eventHandler);
    }

    public void configureDetailMode(String saveText, EventHandler<ActionEvent> saveHandler,
            String resetText, EventHandler<ActionEvent> resetHandler) {
        state.configureDetailMode(saveText, saveHandler, resetText, resetHandler);
    }

    public void addTo(Pane pane) {
        if (!isInit()) {
            try {
                init();
            } catch (Exception e) {
                logger.error("", e);
            }
        }
        detachFromParent();
        resetLayoutState();
        pane.getChildren().add(layout);
        pane.requestLayout();
    }

    public void addTo(Pane pane, int position) {
        if (!isInit()) {
            try {
                init();
            } catch (Exception e) {
                logger.error("", e);
            }
        }
        detachFromParent();
        resetLayoutState();
        pane.getChildren().add(position, layout);
        pane.requestLayout();
    }

    private void detachFromParent() {
        if (layout == null) {
            return;
        }
        Parent parent = layout.getParent();
        if (parent instanceof Pane oldPane) {
            oldPane.getChildren().remove(layout);
        }
    }

    private void resetLayoutState() {
        if (layout == null) {
            return;
        }
        layout.setLayoutX(0.0);
        layout.setLayoutY(0.0);
        layout.setTranslateX(0.0);
        layout.setTranslateY(0.0);
        layout.setManaged(true);
        HBox.setHgrow(layout, null);
        VBox.setVgrow(layout, null);
        FlowPane.setMargin(layout, null);
        HBox.setMargin(layout, null);
        VBox.setMargin(layout, null);
        AnchorPane.setTopAnchor(layout, null);
        AnchorPane.setRightAnchor(layout, null);
        AnchorPane.setBottomAnchor(layout, null);
        AnchorPane.setLeftAnchor(layout, null);
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
