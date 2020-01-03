package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.hwid.NoHWID;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.util.List;

public class LoginScene extends AbstractScene {
    public List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    public Node layout;

    private class AuthConverter extends StringConverter<GetAvailabilityAuthRequestEvent.AuthAvailability> {

        @Override
        public String toString(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
            return authAvailability.displayName;
        }

        @Override
        public GetAvailabilityAuthRequestEvent.AuthAvailability fromString(String s) {
            for (GetAvailabilityAuthRequestEvent.AuthAvailability a : auth)
                if (a.displayName.equals(s)) return a;
            return null;
        }
    }

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doInit() {
        layout = LookupHelper.lookup(scene.getRoot(), "#layout", "#authPane");
        sceneBaseInit(layout);
        TextField loginField = (TextField) layout.lookup("#login");
        if (application.runtimeSettings.login != null) {
            loginField.setText(application.runtimeSettings.login);
        }
        TextField passwordField = (TextField) layout.lookup("#password");
        if (application.runtimeSettings.encryptedPassword != null) {
            passwordField.getStyleClass().add("hasSaved");
            passwordField.setPromptText(application.getLangResource("runtime.scenes.login.login.password.saved"));
            ((CheckBox) layout.lookup("#savePassword")).setSelected(true);
        }
        ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList = (ComboBox) layout.lookup("#combologin");
        authList.setConverter(new AuthConverter());
        ((ButtonBase) layout.lookup("#goAuth")).setOnAction((e) -> contextHelper.runCallback(this::loginWithGui).run());
        //Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            processRequest(application.getLangResource("runtime.overlay.processing.text.launcher"), launcherRequest, (result) -> {
                if (result.needUpdate) {
                    try {
                        LauncherRequest.update(result);
                    } catch (IOException e) {
                        LogHelper.error(e);
                    } catch (Throwable ignored) {

                    }
                    Platform.exit();
                }
                LogHelper.dev("Launcher update processed");
                GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
                processRequest(application.getLangResource("runtime.overlay.processing.text.authAvailability"), getAvailabilityAuthRequest, (auth) -> contextHelper.runInFxThread(() -> {
                    this.auth = auth.list;
                    int authIndex = 0;
                    int i = 0;
                    for (GetAvailabilityAuthRequestEvent.AuthAvailability a : auth.list) {
                        if (a.equals(application.runtimeSettings.lastAuth)) authIndex = i;
                        authList.getItems().add(a);
                        i++;
                    }
                    authList.getSelectionModel().select(authIndex);
                    hideOverlay(0, null);
                }), null);
            }, null);
        }
    }

    @SuppressWarnings("unchecked")
    public void loginWithGui() {
        String login = ((TextField) layout.lookup("#login")).getText();
        TextField passwordField = ((TextField) layout.lookup("#password"));
        byte[] encryptedPassword;
        if (passwordField.getPromptText().equals(application.getLangResource("runtime.scenes.login.login.password.saved"))) {
            encryptedPassword = application.runtimeSettings.encryptedPassword;
        } else {
            String password = passwordField.getText();
            try {
                encryptedPassword = encryptPassword(password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList = (ComboBox) layout.lookup("#combologin");
        String auth_id = authList.getSelectionModel().getSelectedItem().name;
        boolean savePassword = ((CheckBox) layout.lookup("#savePassword")).isSelected();
        login(login, encryptedPassword, auth_id, savePassword);
    }

    public byte[] encryptPassword(String password) throws Exception {
        return SecurityHelper.encrypt(launcherConfig.passwordEncryptKey, password);
    }

    public void login(String login, byte[] password, String auth_id, boolean savePassword) {
        LogHelper.dev("Auth with %s password ***** auth_id %s", login, auth_id);
        AuthRequest authRequest = new AuthRequest(login, password, new NoHWID(), auth_id);
        processRequest(application.getLangResource("runtime.overlay.processing.text.auth"), authRequest, (result) -> {
            application.runtimeStateMachine.setAuthResult(result);
            if (savePassword) {
                application.runtimeSettings.login = login;
                application.runtimeSettings.encryptedPassword = password;
            }
            onGetProfiles();

        }, null);
    }

    public void onGetProfiles() {
        processRequest(application.getLangResource("runtime.overlay.processing.text.profiles"), new ProfilesRequest(), (profiles) -> {
            application.runtimeStateMachine.setProfilesResult(profiles);
            contextHelper.runInFxThread(() -> {
                hideOverlay(0, null);
                application.setMainScene(application.gui.serverMenuScene);
            });
        }, null);
    }
}
