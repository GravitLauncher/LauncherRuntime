package pro.gravit.launcher.client.gui.scene;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.request.auth.password.AuthECPassword;
import pro.gravit.launcher.request.auth.password.AuthTOTPPassword;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class LoginScene extends AbstractScene {
    public boolean isLoginStarted;
    private List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    private TextField loginField;
    private TextField passwordField;
    private CheckBox savePasswordCheckBox;
    private CheckBox autoenter;
    private Pane authActive;
    private Pane layout;
    private ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList;

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
    }

    @Override
    public void doInit() {
        layout = LookupHelper.lookup(scene.getRoot(), "#layout");
        sceneBaseInit(layout);
        authActive = LookupHelper.lookup(layout, "#authActive");
        loginField = LookupHelper.lookup(layout, "#auth", "#login");
        if (application.runtimeSettings.login != null) {
            loginField.setText(application.runtimeSettings.login);
            showAuthButton();;
        }
        loginField.textProperty().addListener((e) -> {
            if(!loginField.getText().isEmpty()) {
                showAuthButton();
            }
            else {
                hideAuthButton();
            }
        });
        passwordField = LookupHelper.lookup(layout, "#auth", "#password");
        savePasswordCheckBox = LookupHelper.lookup(layout , "#leftPane", "#savePassword");
        if (application.runtimeSettings.encryptedPassword != null) {
            passwordField.getStyleClass().add("hasSaved");
            passwordField.setPromptText(application.getTranslation("runtime.scenes.login.login.password.saved"));
            LookupHelper.<CheckBox>lookup(layout, "#leftPane", "#savePassword").setSelected(true);
        }
        autoenter = LookupHelper.<CheckBox>lookup(layout, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());
        if (application.guiModuleConfig.createAccountURL != null)
            LookupHelper.<Text>lookup(layout, "#header", "#controls", "#links", "#registerPane","#createAccount").setOnMouseClicked((e) ->
                    application.openURL(application.guiModuleConfig.createAccountURL));
        if (application.guiModuleConfig.forgotPassURL != null)
            LookupHelper.<Text>lookup(layout, "#header", "#controls", "#links", "#forgotPass").setOnMouseClicked((e) ->
                    application.openURL(application.guiModuleConfig.forgotPassURL));
        authList = LookupHelper.lookup(layout, "#combologin");
        authList.setConverter(new AuthConverter());
        LookupHelper.<ButtonBase>lookup(authActive, "#authButton").setOnAction((e) -> contextHelper.runCallback(this::loginWithGui).run());
        // Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            processRequest(application.getTranslation("runtime.overlay.processing.text.launcher"), launcherRequest, (result) -> {
                if (result.needUpdate) {
                    try {
                        LogHelper.debug("Start update processing");
                        application.securityService.update(result);
                        LogHelper.debug("Exit with Platform.exit");
                        Platform.exit();
                        return;
                    } catch (Throwable e) {
                        contextHelper.runInFxThread(() -> {
                            getCurrentOverlay().errorHandle(e);
                        });
                        try {
                            Thread.sleep(1500);
                            LauncherEngine.modulesManager.invokeEvent(new ClientExitPhase(0));
                            Platform.exit();
                        } catch (Throwable ex) {
                            LauncherEngine.exitLauncher(0);
                        }
                    }
                }
                LogHelper.dev("Launcher update processed");
                GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
                processRequest(application.getTranslation("runtime.overlay.processing.text.authAvailability"), getAvailabilityAuthRequest, (auth) -> contextHelper.runInFxThread(() -> {
                    this.auth = auth.list;
                    GetAvailabilityAuthRequestEvent.AuthAvailability lastAuth = null;
                    for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth.list) {
                        if (authAvailability.equals(application.runtimeSettings.lastAuth))
                            lastAuth = authAvailability;
                        authList.getItems().add(authAvailability);
                    }
                    if (lastAuth != null) authList.getSelectionModel().select(lastAuth);
                    else authList.getSelectionModel().selectFirst();

                    hideOverlay(0, (event) -> {
                        if (application.runtimeSettings.encryptedPassword != null && application.runtimeSettings.autoAuth)
                            contextHelper.runCallback(this::loginWithGui).run();
                    });
                }), null);
            }, (event) -> LauncherEngine.exitLauncher(0));
        }
    }

    public void showAuthButton() {
        authActive.setVisible(true);
    }

    public void hideAuthButton() {
        authActive.setVisible(false);
    }

    private volatile boolean processingEnabled = false;

    public<T extends WebSocketEvent> void processing(Request<T> request, String text, Consumer<T> onSuccess, Consumer<String> onError) {
        Pane root = (Pane) scene.getRoot();
        Button authButton = LookupHelper.lookup(processingEnabled ? root : authActive, "#authButton");
        Pane blur = LookupHelper.<Pane>lookup(layout, "#blur");
        LookupHelper.Point2D authAbsPosition = LookupHelper.getAbsoluteCords(authButton, layout);
        LogHelper.debug("X: %f, Y: %f",authAbsPosition.x, authAbsPosition.y);
        double authLayoutX = authButton.getLayoutX();
        double authLayoutY = authButton.getLayoutY();
        String oldText = authButton.getText();
        if(!processingEnabled) {
            contextHelper.runInFxThread(() -> {
                blur.setVisible(true);
                layout.setEffect(new GaussianBlur(20));
                authActive.getChildren().remove(authButton);
                root.getChildren().add(authButton);
                authButton.setLayoutX(authAbsPosition.x);
                authButton.setLayoutY(authAbsPosition.y);
            });
            processingEnabled = true;
        }
        contextHelper.runInFxThread(() -> {
            authButton.setText(text);
        });
        Runnable processingOff = () -> {
            if(!processingEnabled) return;
            contextHelper.runInFxThread(() -> {
                layout.setEffect(null);
                blur.setVisible(false);
                root.getChildren().remove(authButton);
                authActive.getChildren().add(authButton);
                authButton.setLayoutX(authLayoutX);
                authButton.setLayoutY(authLayoutY);
                authButton.setText(oldText);
            });
            processingEnabled = false;
        };
        try {
            Request.service.request(request).thenAccept((result) -> {
                onSuccess.accept(result);
                processingOff.run();
            }).exceptionally((exc) -> {
                LogHelper.error(exc);
                onError.accept(exc.getCause().getMessage());
                processingOff.run();
                return null;
            });
        } catch (IOException e) {
            LogHelper.error(e);
        }
    }

    @Override
    public void reset() {
        passwordField.getStyleClass().removeAll("hasSaved");
        passwordField.setPromptText(application.getTranslation("runtime.scenes.login.login.password"));
        passwordField.setText("");
        loginField.setText("");
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
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
        GetAvailabilityAuthRequestEvent.AuthAvailability authId = authList.getSelectionModel().getSelectedItem();
        boolean savePassword = savePasswordCheckBox.isSelected();
        login(login, encryptedPassword, authId, null, savePassword);
    }

    private byte[] encryptPassword(String password) throws Exception {
        return SecurityHelper.encrypt(launcherConfig.passwordEncryptKey, password);
    }

    private void login(String login, byte[] password, GetAvailabilityAuthRequestEvent.AuthAvailability authId, String totp, boolean savePassword) {
        isLoginStarted = true;
        LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
        AuthRequest authRequest;
        if(totp == null)
        {
            authRequest = new AuthRequest(login, password, authId.name);
        }
        else
        {
            Auth2FAPassword auth2FAPassword = new Auth2FAPassword();
            auth2FAPassword.firstPassword = new AuthECPassword(password);
            auth2FAPassword.secondPassword = new AuthTOTPPassword();
            ((AuthTOTPPassword) auth2FAPassword.secondPassword).totp = totp;
            authRequest = new AuthRequest(login, auth2FAPassword, authId.name, true, AuthRequest.ConnectTypes.CLIENT);
        }
        processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
            application.runtimeStateMachine.setAuthResult(result);
            if (savePassword) {
                application.runtimeSettings.login = login;
                application.runtimeSettings.encryptedPassword = password;
                application.runtimeSettings.lastAuth = authId;
            }
            onGetProfiles();

        }, (error) -> {
            LogHelper.info("Handle error: ", error);
            if(totp != null) {
                application.messageManager.createNotification(application.getTranslation("runtime.scenes.login.dialog2fa.header"), error);
                return;
            }
            if(error.equals(AuthRequestEvent.TWO_FACTOR_NEED_ERROR_MESSAGE))
            {
                this.hideOverlay(0, null); //Force hide overlay
                application.messageManager.showTextDialog(application.getTranslation("runtime.scenes.login.dialog2fa.header"), (result) -> {
                    login(login, password, authId, result, savePassword);
                }, null, true);
            }
        });
    }

    public void onGetProfiles() {
        processing(new ProfilesRequest(), application.getTranslation("runtime.overlay.processing.text.profiles"), (profiles) -> {
            application.runtimeStateMachine.setProfilesResult(profiles);
            contextHelper.runInFxThread(() -> {
                hideOverlay(0, null);
                application.securityService.startRequest();
                if (application.gui.optionsOverlay != null) {
                    try {
                        application.gui.optionsOverlay.loadAll();
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
    }

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
}
