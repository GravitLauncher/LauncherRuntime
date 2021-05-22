package pro.gravit.launcher.client.gui.scenes;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.service.AuthService;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.auth.password.*;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class LoginScene extends AbstractScene {
    public boolean isLoginStarted;
    private List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    private TextField loginField;
    private TextField passwordField;
    private CheckBox savePasswordCheckBox;
    private CheckBox autoenter;
    private Pane authActive;
    private Button authButton;
    private final AuthService authService = new AuthService(application);
    private VBox authList;
    private ToggleGroup authToggleGroup;
    private GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability;

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
    }

    @Override
    public void doInit() {
        authActive = LookupHelper.lookup(layout, "#authActive");
        authButton = LookupHelper.lookup(authActive, "#authButton");
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
        if (application.runtimeSettings.password != null) {
            passwordField.getStyleClass().add("hasSaved");
            passwordField.setPromptText(application.getTranslation("runtime.scenes.login.password.saved"));
            LookupHelper.<CheckBox>lookup(layout, "#leftPane", "#savePassword").setSelected(true);
        }
        autoenter = LookupHelper.<CheckBox>lookup(layout, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());
        if (application.guiModuleConfig.createAccountURL != null)
            LookupHelper.<Text>lookup(header,  "#controls", "#links", "#registerPane","#createAccount").setOnMouseClicked((e) ->
                    application.openURL(application.guiModuleConfig.createAccountURL));
        if (application.guiModuleConfig.forgotPassURL != null)
            LookupHelper.<Text>lookup(header,  "#controls", "#links", "#forgotPass").setOnMouseClicked((e) ->
                    application.openURL(application.guiModuleConfig.forgotPassURL));
        authList = (VBox) LookupHelper.<ScrollPane>lookup(layout, "#authList").getContent();
        authToggleGroup = new ToggleGroup();
        LookupHelper.<ButtonBase>lookup(authActive, "#authButton").setOnAction((e) -> contextHelper.runCallback(this::loginWithGui));
        // Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            if(!application.isDebugMode())
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
                    authList.setVisible(auth.list.size() != 1);
                    for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth.list) {
                        if(application.runtimeSettings.lastAuth == null) {
                            if(authAvailability.name.equals("std") || this.authAvailability == null) {
                                this.authAvailability = authAvailability;
                            }
                        }
                        else if (authAvailability.name.equals(application.runtimeSettings.lastAuth.name))
                            this.authAvailability = authAvailability;
                        addAuthAvailability(authAvailability);
                    }

                    hideOverlay(0, (event) -> {
                        if (application.runtimeSettings.password != null && application.runtimeSettings.autoAuth)
                            contextHelper.runCallback(this::loginWithGui);
                    });
                }), null);
            }, (event) -> LauncherEngine.exitLauncher(0));
        }
    }

    public void addAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
        RadioButton radio = new RadioButton();
        radio.setToggleGroup(authToggleGroup);
        radio.setId("authRadio");
        radio.setText(authAvailability.displayName);
        if(this.authAvailability == authAvailability) {
            radio.fire();
        }
        radio.setOnAction((e) -> {
            LogHelper.info("Selected auth: %s", authAvailability.name);
            this.authAvailability = authAvailability;
        });
        LogHelper.info("Added %s: %s", authAvailability.name, authAvailability.displayName);
        authList.getChildren().add(radio);
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
        LookupHelper.Point2D authAbsPosition = LookupHelper.getAbsoluteCords(authButton, layout);
        LogHelper.debug("X: %f, Y: %f",authAbsPosition.x, authAbsPosition.y);
        double authLayoutX = authButton.getLayoutX();
        double authLayoutY = authButton.getLayoutY();
        String oldText = authButton.getText();
        if(!processingEnabled) {
            contextHelper.runInFxThread(() -> {
                disable();
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
                enable();
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
                onError.accept(exc.getCause().getMessage());
                processingOff.run();
                return null;
            });
        } catch (IOException e) {
            processingOff.run();
            errorHandle(e);
        }
    }


    @Override
    public void errorHandle(Throwable e) {
        super.errorHandle(e);
    }

    @Override
    public void reset() {
        passwordField.getStyleClass().removeAll("hasSaved");
        passwordField.setPromptText(application.getTranslation("runtime.scenes.login.password"));
        passwordField.setText("");
        loginField.setText("");
    }

    @Override
    public String getName() {
        return "login";
    }

    private void loginWithGui() {
        String login = loginField.getText();
        AuthRequest.AuthPasswordInterface password;
        if (passwordField.getText().isEmpty() && passwordField.getPromptText().equals(
                application.getTranslation("runtime.scenes.login.password.saved"))) {
            password = application.runtimeSettings.password;
        } else {
            String rawPassword = passwordField.getText();
            password = authService.makePassword(rawPassword);
        }
        boolean savePassword = savePasswordCheckBox.isSelected();
        login(login, password, authAvailability, null, savePassword);

    }

    private void login(String login, AuthRequest.AuthPasswordInterface password, GetAvailabilityAuthRequestEvent.AuthAvailability authId, String totp, boolean savePassword) {
        isLoginStarted = true;
        LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
        AuthRequest authRequest;
        if(totp == null)
        {
            authRequest = authService.makeAuthRequest(login, password, "std");
        }
        else
        {
            AuthRequest.AuthPasswordInterface auth2FAPassword = authService.make2FAPassword(password, totp);
            authRequest = authService.makeAuthRequest(login, auth2FAPassword, "std");
        }
        processing(authRequest, application.getTranslation("runtime.overlay.processing.text.auth"), (result) -> {
            application.stateService.setAuthResult(result);
            if (savePassword) {
                application.runtimeSettings.login = login;
                if(password instanceof AuthAESPassword) //TODO: Check if save possibly
                    application.runtimeSettings.password = password;
                application.runtimeSettings.lastAuth = authId;
            }
            if(result.playerProfile != null && result.playerProfile.skin != null) {
                try {
                    application.skinManager.addSkin(result.playerProfile.username, new URL(result.playerProfile.skin.url));
                    application.skinManager.getSkin(result.playerProfile.username); //Cache skin
                } catch (Exception ignored) {
                }
            }
            contextHelper.runInFxThread(() -> {
                Optional<Node> player = LookupHelper.lookupIfPossible(scene.getRoot(), "#player");
                if(player.isPresent()) {
                    LookupHelper.<Labeled>lookupIfPossible(player.get(), "#playerName").ifPresent(l -> l.setText(result.playerProfile.username.toUpperCase(Locale.ROOT)));
                    LookupHelper.<ImageView>lookupIfPossible(player.get(), "#playerHead").ifPresent(
                            (h) -> {
                                try {
                                    Image image = application.skinManager.getScaledFxSkinHead(result.playerProfile.username, (int) h.getFitWidth(), (int) h.getFitHeight());
                                    h.setImage(image);
                                } catch (Throwable e) {
                                    LogHelper.warning("Skin head error");
                                }
                            }
                    );
                    player.get().setVisible(true);
                    disable();
                    fade(player.get(), 2000.0, 0.0, 1.0, (e) -> {
                        enable();
                        onGetProfiles();
                        player.get().setVisible(false);
                    }
                    );
                } else {
                    onGetProfiles();
                }
            });

        }, (error) -> {
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
            } else {
                errorHandle(new RequestException(error));
            }
        });
    }

    public void onGetProfiles() {
        processing(new ProfilesRequest(), application.getTranslation("runtime.overlay.processing.text.profiles"), (profiles) -> {
            application.stateService.setProfilesResult(profiles);
            contextHelper.runInFxThread(() -> {
                hideOverlay(0, null);
                application.securityService.startRequest();
                if (application.gui.optionsScene != null) {
                    try {
                        application.gui.optionsScene.loadAll();
                    } catch (Throwable ex) {
                        errorHandle(ex);
                    }
                }
                if (application.getCurrentScene() instanceof LoginScene) {
                    ((LoginScene) application.getCurrentScene()).isLoginStarted = false;
                }
                application.setMainScene(application.gui.serverMenuScene);
            });
        }, null);
    }

    @SuppressWarnings("deprecation")
    public void clearPassword() {
        application.runtimeSettings.encryptedPassword = null;
        application.runtimeSettings.password = null;
        application.runtimeSettings.login = null;
    }
}
