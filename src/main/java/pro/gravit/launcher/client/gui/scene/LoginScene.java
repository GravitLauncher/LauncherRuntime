package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.hwid.NoHWID;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.util.List;

public class LoginScene extends AbstractScene {
    public List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    public Node layout;
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
        layout = scene.lookup("#loginPane").lookup("#layout").lookup("#authPane");
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
        ((ButtonBase)layout.lookup("#goAuth") ).setOnAction((e) -> contextHelper.runCallback(this::loginWithGui).run());
        //Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            processRequest("Launcher update", launcherRequest, (result) -> {
                LogHelper.dev("Launcher update processed");
                GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
                processRequest("AuthAvailability update", getAvailabilityAuthRequest, (auth) -> {
                    contextHelper.runInFxThread(() -> {
                        this.auth = auth.list;
                        int authIndex = 0;
                        int i = 0;
                        for(GetAvailabilityAuthRequestEvent.AuthAvailability a : auth.list)
                        {
                            if(a.name.equals(application.settings.auth)) authIndex = i;
                            authList.getItems().add(a);
                            i++;
                        }
                        authList.getSelectionModel().select(authIndex);
                        hideOverlay(0, null);
                    });
                }, null);
            }, null);
        }
    }
    @SuppressWarnings("unchecked")
    public void loginWithGui() throws Exception {
        String login = ((TextField)layout.lookup("#login")).getText();
        TextField passwordField = ((TextField)layout.lookup("#password"));
        byte[] encryptedPassword;
        if(passwordField.getPromptText().equals("*** Сохранённый ***"))
        {
            encryptedPassword = application.settings.rsaPassword;
        }
        else
        {
            String password = passwordField.getText();
            try {
                encryptedPassword = encryptPassword(password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList = (ComboBox) layout.lookup("#combologin");
        String auth_id = authList.getSelectionModel().getSelectedItem().name;
        login(login, encryptedPassword, auth_id);
    }
    public byte[] encryptPassword(String password) throws Exception {
        return SecurityHelper.encrypt(launcherConfig.passwordEncryptKey, password);
    }
    public void login(String login, byte[] password, String auth_id)
    {
        LogHelper.dev("Auth with %s password ***** auth_id %s", login, auth_id);
        AuthRequest authRequest = new AuthRequest(login, password, new NoHWID(), auth_id);
        processRequest("Auth", authRequest, (result) -> {
            LogHelper.dev("TODO: Auth %s success", result.playerProfile.username);
            application.runtimeStateMachine.setAuthResult(result);
            hideOverlay(0, null);
        }, null);
    }
}
