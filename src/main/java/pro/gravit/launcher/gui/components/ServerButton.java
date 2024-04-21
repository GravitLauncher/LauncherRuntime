package pro.gravit.launcher.gui.components;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.gui.utils.JavaFxUtils;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.utils.helper.LogHelper;

import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;

public class ServerButton extends AbstractVisualComponent {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private static final String SERVER_BUTTON_DEFAULT_IMAGE = "images/servers/example.png";
    private static final String SERVER_BUTTON_CUSTOM_IMAGE = "images/servers/%s.png";
    public ClientProfile profile;
    private Button saveButton;
    private Button resetButton;
    private Region serverLogo;

    protected ServerButton(JavaFXApplication application, ClientProfile profile) {
        super(getServerButtonFxml(application, profile), application);
        this.profile = profile;
    }

    public static ServerButton createServerButton(JavaFXApplication application, ClientProfile profile) {
        return new ServerButton(application, profile);
    }

    private static String getServerButtonFxml(JavaFXApplication application, ClientProfile profile) {
        String customFxml = String.format(SERVER_BUTTON_CUSTOM_FXML, profile.getUUID().toString());
        URL fxml = application.tryResource(customFxml);
        if(fxml != null) {
            return customFxml;
        }
        return SERVER_BUTTON_FXML;
    }

    @Override
    public String getName() {
        return "serverButton";
    }

    @Override
    protected void doInit() {
        LookupHelper.<Labeled>lookup(layout, "#nameServer").setText(profile.getTitle());
        LookupHelper.<Labeled>lookup(layout, "#genreServer").setText(profile.getVersion().toString());
        this.serverLogo = LookupHelper.lookup(layout, "#serverLogo");
        URL logo = application.tryResource(String.format(SERVER_BUTTON_CUSTOM_IMAGE, profile.getUUID().toString()));
        if(logo == null) {
            logo = application.tryResource(SERVER_BUTTON_DEFAULT_IMAGE);
        }
        if(logo != null) {
            this.serverLogo.setBackground(new Background(new BackgroundImage(new Image(logo.toString()),
                                                                             BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                                                                             BackgroundPosition.CENTER, new BackgroundSize(0.0, 0.0, true, true, false, true))));
            JavaFxUtils.setRadius(this.serverLogo, 20.0);
        }
        AtomicLong currentOnline = new AtomicLong(0);
        AtomicLong maxOnline = new AtomicLong(0);
        Runnable update = () -> contextHelper.runInFxThread(() -> {
            if (currentOnline.get() == 0 && maxOnline.get() == 0) {
                LookupHelper.<Labeled>lookup(layout, "#online").setText("?");
            } else {
                LookupHelper.<Labeled>lookup(layout, "#online").setText(String.valueOf(currentOnline.get()));
            }
        });
        for (ClientProfile.ServerProfile serverProfile : profile.getServers()) {
            application.pingService.getPingReport(serverProfile.name).thenAccept((report) -> {
                if (report != null) {
                    currentOnline.addAndGet(report.playersOnline);
                    maxOnline.addAndGet(report.maxPlayers);
                }
                update.run();
            });
        }
        saveButton = LookupHelper.lookup(layout, "#save");
        resetButton = LookupHelper.lookup(layout, "#reset");
    }

    @Override
    protected void doPostInit() {

    }

    public void setOnMouseClicked(EventHandler<? super MouseEvent> eventHandler) {
        layout.setOnMouseClicked(eventHandler);
    }

    public void enableSaveButton(String text, EventHandler<ActionEvent> eventHandler) {
        saveButton.setVisible(true);
        if (text != null) saveButton.setText(text);
        saveButton.setOnAction(eventHandler);
    }

    public void enableResetButton(String text, EventHandler<ActionEvent> eventHandler) {
        resetButton.setVisible(true);
        if (text != null) resetButton.setText(text);
        resetButton.setOnAction(eventHandler);
    }

    public void addTo(Pane pane) {
        if (!isInit()) {
            try {
                init();
            } catch (Exception e) {
                LogHelper.error(e);
            }
        }
        pane.getChildren().add(layout);
    }

    public void addTo(Pane pane, int position) {
        if (!isInit()) {
            try {
                init();
            } catch (Exception e) {
                LogHelper.error(e);
            }
        }
        pane.getChildren().add(position, layout);
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
