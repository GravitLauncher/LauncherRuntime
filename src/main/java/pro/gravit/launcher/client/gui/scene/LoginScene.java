package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.util.List;

public class LoginScene extends AbstractScene {
    private List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    public boolean isLoginStarted;
    private TextField loginField;
    private TextField passwordField;
    private CheckBox savePasswordCheckBox;
    private ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList;

    private class AuthConverter extends StringConverter<GetAvailabilityAuthRequestEvent.AuthAvailability> {

        @Override
        public String toString(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
            return authAvailability.displayName;
        }

        @Override
        public GetAvailabilityAuthRequestEvent.AuthAvailability fromString(String s) {
            for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth)
                if (authAvailability.displayName.equals(s))
                    return authAvailability;
            return null;
        }
    }

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
    }

    @Override
    public void doInit() {
        Node layout = LookupHelper.lookup(scene.getRoot(), "#layout", "#authPane");
        sceneBaseInit(layout);
        loginField = LookupHelper.lookup(layout, "#login");
        if (application.runtimeSettings.login != null)
            loginField.setText(application.runtimeSettings.login);
        passwordField = LookupHelper.lookup(layout, "#password");
        savePasswordCheckBox = LookupHelper.lookup(layout, "#savePassword");
        if (application.runtimeSettings.encryptedPassword != null) {
            passwordField.getStyleClass().add("hasSaved");
            passwordField.setPromptText(application.getTranslation("runtime.scenes.login.login.password.saved"));
            LookupHelper.<CheckBox>lookup(layout, "#savePassword").setSelected(true);
        }
        if (application.guiModuleConfig.createAccountURL != null)
            LookupHelper.<Hyperlink>lookup(layout, "#createAccount").setOnAction((e) ->
                    application.openURL(application.guiModuleConfig.createAccountURL));
        if (application.guiModuleConfig.forgotPassURL != null)
            LookupHelper.<Hyperlink>lookup(layout, "#forgotPass").setOnAction((e) ->
                    application.openURL(application.guiModuleConfig.forgotPassURL));
        authList = LookupHelper.lookup(layout, "#combologin");
        authList.setConverter(new AuthConverter());
        LookupHelper.<ButtonBase>lookup(layout, "#goAuth").setOnAction((e) -> contextHelper.runCallback(this::loginWithGui).run());
        // Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            processRequest(application.getTranslation("runtime.overlay.processing.text.launcher"), launcherRequest, (result) -> {
                if (result.needUpdate) {
                    try {
                        application.securityService.update(result);
                    } catch (IOException e) {
                        LogHelper.error(e);
                    } catch (Throwable ignored) {

                    }
                    try {
                        LauncherEngine.exitLauncher(0);
                    } catch (Throwable e) {
                        Platform.exit();
                    }
                }
                LogHelper.dev("Launcher update processed");
                GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
                processRequest(application.getTranslation("runtime.overlay.processing.text.authAvailability"), getAvailabilityAuthRequest, (auth) -> contextHelper.runInFxThread(() -> {
                    this.auth = auth.list;
                    int authIndex = 0;
                    int i = 0;
                    for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth.list) {
                        if (authAvailability.equals(application.runtimeSettings.lastAuth))
                            authIndex = i;
                        authList.getItems().add(authAvailability);
                        i++;
                    }
                    authList.getSelectionModel().select(authIndex);
                    hideOverlay(0, null);
                }), null);
            }, (e) -> LauncherEngine.exitLauncher(0));
        }
    }

    private void loginWithGui() {
        String login = loginField.getText();
        byte[] encryptedPassword;
        if (passwordField.getText().isEmpty() && passwordField.getPromptText().equals(
                application.getTranslation("runtime.scenes.login.login.password.saved"))) {
            encryptedPassword = application.runtimeSettings.encryptedPassword;
        } else {
            String password = passwordField.getText();
            try {
                encryptedPassword = encryptPassword(password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String authId = authList.getSelectionModel().getSelectedItem().name;
        boolean savePassword = savePasswordCheckBox.isSelected();
        login(login, encryptedPassword, authId, savePassword);
    }

    private byte[] encryptPassword(String password) throws Exception {
        return SecurityHelper.encrypt(launcherConfig.passwordEncryptKey, password);
    }

    private void login(String login, byte[] password, String authId, boolean savePassword) {
        isLoginStarted = true;
        LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
        AuthRequest authRequest = new AuthRequest(login, password, authId);
        processRequest(application.getTranslation("runtime.overlay.processing.text.auth"), authRequest, (result) -> {
            application.runtimeStateMachine.setAuthResult(result);
            if (savePassword) {
                application.runtimeSettings.login = login;
                application.runtimeSettings.encryptedPassword = password;
            }
            onGetProfiles();

        }, null);
    }

    public void onGetProfiles() {
        processRequest(application.getTranslation("runtime.overlay.processing.text.profiles"), new ProfilesRequest(), (profiles) -> {
            application.runtimeStateMachine.setProfilesResult(profiles);
            contextHelper.runInFxThread(() -> {
                hideOverlay(0, null);
                application.securityService.startRequest();
                if (application.gui.optionsScene != null) {
                    try {
                        application.gui.optionsScene.loadAll();
                    } catch (Throwable ex) {
                        LogHelper.error(ex);
                    }
                }
                if (application.getCurrentScene() instanceof LoginScene) {
                    ((LoginScene) application.getCurrentScene()).isLoginStarted = false;
                }
                application.setMainScene(application.gui.serverMenuScene);
            });
        }, null);
    }

    public void clearPassword() {
        application.runtimeSettings.encryptedPassword = null;
        application.runtimeSettings.login = null;
        passwordField.getStyleClass().removeAll("hasSaved");
        passwordField.setText("");
        loginField.setText("");
    }
}
