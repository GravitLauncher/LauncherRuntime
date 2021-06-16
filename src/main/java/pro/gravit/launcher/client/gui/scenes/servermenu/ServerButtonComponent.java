package pro.gravit.launcher.client.gui.scenes.servermenu;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.LogHelper;

import java.net.URL;

public class ServerButtonComponent extends AbstractVisualComponent {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    public ClientProfile profile;
    private Button saveButton;
    private Button resetButton;
    protected ServerButtonComponent(JavaFXApplication application, ClientProfile profile) {
        super(getFXMLPath(application, profile), application);
        this.profile = profile;
    }

    private static String getFXMLPath(JavaFXApplication application, ClientProfile profile) {
        String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profile.getUUID());
        URL customFxml = application.tryResource(customFxmlName);
        if (customFxml != null) {
            return customFxmlName;
        }
        return SERVER_BUTTON_FXML;
    }

    @Override
    public String getName() {
        return "serverButton";
    }

    @Override
    protected void doInit() throws Exception {
        LookupHelper.<Labeled>lookup(layout, "#nameServer").setText(profile.getTitle());
        LookupHelper.<Labeled>lookup(layout, "#genreServer").setText(profile.getVersion().toString());
        LookupHelper.<ImageView>lookupIfPossible(layout, "#serverLogo").ifPresent((a) -> {
            try {
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(a.getFitWidth(), a.getFitHeight());
                clip.setArcWidth(20.0);
                clip.setArcHeight(20.0);
                a.setClip(clip);
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        });
        application.stateService.addServerPingCallback(profile.getDefaultServerProfile().name, (report) -> {
            LookupHelper.<Labeled>lookup(layout, "#online").setText(String.valueOf(report.playersOnline));
        });
        saveButton = LookupHelper.lookup(layout, "#save");
        resetButton = LookupHelper.lookup(layout, "#reset");
    }

    public void setOnMouseClicked(EventHandler<? super MouseEvent> eventHandler) {
        layout.setOnMouseClicked(eventHandler);
    }

    public void enableSaveButton(String text, EventHandler<ActionEvent> eventHandler) {
        saveButton.setVisible(true);
        if(text != null)
            saveButton.setText(text);
        saveButton.setOnAction(eventHandler);
    }

    public void enableResetButton(String text, EventHandler<ActionEvent> eventHandler) {
        resetButton.setVisible(true);
        if(text != null)
            resetButton.setText(text);
        resetButton.setOnAction(eventHandler);
    }

    public void addTo(Pane pane) {
        if(!isInit()) {
            try {
                init();
            } catch (Exception e) {
                LogHelper.error(e);
            }
        }
        pane.getChildren().add(layout);
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
