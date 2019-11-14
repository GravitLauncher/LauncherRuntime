package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.utils.helper.LogHelper;

import java.util.List;

public class LoginScene extends AbstractScene {
    public List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    private class AuthConverter extends StringConverter<GetAvailabilityAuthRequestEvent.AuthAvailability>
    {

        @Override
        public String toString(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
            return authAvailability.displayName;
        }

        @Override
        public GetAvailabilityAuthRequestEvent.AuthAvailability fromString(String s) {
            for(GetAvailabilityAuthRequestEvent.AuthAvailability a : auth)
                if(a.displayName.equals(s)) return a;
            return null;
        }
    }
    public LoginScene(Scene scene, Stage stage, JavaFXApplication application) {
        super(scene, stage, application);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init() throws Exception {
        Node layout = scene.lookup("#loginPane").lookup("#layout").lookup("#authPane");
        ((ButtonBase)layout.lookup("#close") ).setOnAction((e) -> {
            Platform.exit();
        });
        ((ButtonBase)layout.lookup("#hide") ).setOnAction((e) -> {
            stage.setIconified(true);
        });
        TextField loginField = (TextField) layout.lookup("#login");
        if(application.settings.login != null)
        {
            loginField.setText(application.settings.login);
        }
        TextField passwordField = (TextField) layout.lookup("#password");
        if (application.settings.rsaPassword != null) {
            passwordField.getStyleClass().add("hasSaved");
            passwordField.setPromptText("*** Сохранённый ***");
        }
        ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList = (ComboBox) layout.lookup("#combologin");
        authList.setConverter(new AuthConverter());
        //Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            processRequest("Launcher update", launcherRequest, (result) -> {
                LogHelper.dev("Launcher update processed");
                GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
                processRequest("AuthAvailability update", getAvailabilityAuthRequest, (auth) -> {
                    this.auth = auth.list;
                    for(GetAvailabilityAuthRequestEvent.AuthAvailability a : auth.list)
                    {
                        authList.getItems().add(a);
                    }
                    hideOverlay(0, null);
                }, null);
            }, null);
        }
    }
}
