package pro.gravit.launcher.gui.scenes.options;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.base.events.request.GetAssetUploadUrlRequestEvent;
import pro.gravit.launcher.base.request.cabinet.AssetUploadInfoRequest;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.DesignConstants;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.AbstractScene;
import pro.gravit.launcher.gui.scenes.servermenu.ServerButton;
import pro.gravit.launcher.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.gui.utils.JavaFxUtils;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.base.profiles.optional.OptionalFile;
import pro.gravit.launcher.base.profiles.optional.OptionalView;

import java.util.*;
import java.util.function.Consumer;

public class OptionsScene extends AbstractScene {
    private TabPane tabPane;
    private final Map<String, Tab> tabs = new HashMap<>();
    private OptionalView optionalView;
    private ImageView avatar;
    private Image originalAvatarImage;

    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        tabPane = LookupHelper.lookup(layout, "#tabPane");
        /** -- UserBlock START -- */
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent((h) -> {
            try {
                JavaFxUtils.setStaticRadius(h, DesignConstants.AVATAR_IMAGE_RADIUS);
                h.setImage(originalAvatarImage);
            } catch (Throwable e) {
                LogHelper.warning("Skin head error");
            }
        });
        /** -- UserBlock END -- */
    }

    @Override
    public void reset() {
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.profilesService.getProfile();
        ServerButton serverButton = ServerMenuScene.getServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(null, (e) -> {
            try {
                application.profilesService.setOptionalView(profile, optionalView);
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        serverButton.enableResetButton(null, (e) -> {
            tabPane.getTabs().clear();
            tabs.clear();
            application.profilesService.setOptionalView(profile, new OptionalView(profile));
            addProfileOptionals(application.profilesService.getOptionalView());
        });
        tabPane.getTabs().clear();
        tabs.clear();
        LookupHelper.<Button>lookupIfPossible(layout, "#back").ifPresent(x -> x.setOnAction((e) -> {
            try {
                switchScene(application.gui.serverInfoScene);
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        addProfileOptionals(application.profilesService.getOptionalView());
        /** -- UserBlock START -- */
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname")
                    .ifPresent((e) -> e.setText(application.authService.getUsername()));
        LookupHelper.<Label>lookupIfPossible(layout, "#role")
                    .ifPresent((e) -> e.setText(application.authService.getMainRole()));
        avatar.setImage(originalAvatarImage);
        resetAvatar();
        if(application.authService.isFeatureAvailable(GetAssetUploadUrlRequestEvent.FEATURE_NAME)) {
            LookupHelper.<Button>lookupIfPossible(layout, "#customization").ifPresent((h) -> {
                h.setVisible(true);
                h.setOnAction((a) -> {
                    processRequest(application.getTranslation("runtime.overlay.processing.text.uploadassetinfo"), new AssetUploadInfoRequest(), (info) -> {
                        contextHelper.runInFxThread(() -> {
                            showOverlay(application.gui.uploadAssetOverlay, (f) -> {
                                application.gui.uploadAssetOverlay.onAssetUploadInfo(info);
                            });
                        });
                    }, this::errorHandle, (e) -> {});
                });
            });
        }
        /** -- UserBlock END -- */
    }

    public void resetAvatar() {
        if (avatar == null) {
            return;
        }
        JavaFxUtils.putAvatarToImageView(application, application.authService.getUsername(), avatar);
    }

    @Override
    public String getName() {
        return "options";
    }

    private final Map<OptionalFile, Consumer<Boolean>> watchers = new HashMap<>();

    private void callWatcher(OptionalFile file, Boolean value) {
        for (Map.Entry<OptionalFile, Consumer<Boolean>> v : watchers.entrySet()) {
            if (v.getKey() == file) {
                v.getValue().accept(value);
                break;
            }
        }
    }

    public void addProfileOptionals(OptionalView view) {
        this.optionalView = new OptionalView(view);
        watchers.clear();
        for (OptionalFile optionalFile : optionalView.all) {
            if (!optionalFile.visible) continue;
            List<String> libraries = optionalFile.dependencies == null ? List.of() : Arrays.stream(
                    optionalFile.dependencies).map(OptionalFile::getName).toList();

            Consumer<Boolean> setCheckBox =
                    add(optionalFile.category == null ? "GLOBAL" : optionalFile.category, optionalFile.name, optionalFile.info, optionalView.enabled.contains(optionalFile),
                        optionalFile.subTreeLevel,
                        (isSelected) -> {
                            if (isSelected) optionalView.enable(optionalFile, true, this::callWatcher);
                            else optionalView.disable(optionalFile, this::callWatcher);
                        }, libraries);
            watchers.put(optionalFile, setCheckBox);
        }
    }

    public VBox addTab(String name, String displayName) {
        Tab tab = new Tab();
        tab.setText(displayName);
        VBox vbox = new VBox();
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(vbox);
        scrollPane.setFitToWidth(true);
        tab.setContent(scrollPane);
        tabs.put(name, tab);
        tabPane.getTabs().add(tab);
        return vbox;
    }

    public Consumer<Boolean> add(String tab, String name, String description, boolean value, int padding,
            Consumer<Boolean> onChanged, List<String> libraries) {
        VBox vBox = new VBox();
        CheckBox checkBox = new CheckBox();
        Label label = new Label();
        vBox.getChildren().add(checkBox);
        vBox.getChildren().add(label);
        VBox.setMargin(vBox, new Insets(0, 0, 0, 30 * --padding));
        vBox.setOnMouseClicked((e) -> {
            if(e.getButton() == MouseButton.PRIMARY) {
                checkBox.setSelected(!checkBox.isSelected());
                onChanged.accept(checkBox.isSelected());
            }
        });
        vBox.setOnTouchPressed((e) -> {
            checkBox.setSelected(!checkBox.isSelected());
            onChanged.accept(checkBox.isSelected());
        });
        vBox.getStyleClass().add("optional-container");
        checkBox.setSelected(value);
        checkBox.setText(name);
        checkBox.setOnAction((e) -> {onChanged.accept(checkBox.isSelected());});
        checkBox.getStyleClass().add("optional-checkbox");
        label.setText(description);
        label.setWrapText(true);
        label.getStyleClass().add("optional-label");
        if(!libraries.isEmpty()) {
            HBox hBox = new HBox();
            hBox.getStyleClass().add("optional-library-container");
            for(var l : libraries) {
                Label lib = new Label();
                lib.setText(l);
                lib.getStyleClass().add("optional-library");
                hBox.getChildren().add(lib);
            }
            vBox.getChildren().add(hBox);
        }
        VBox components;
        boolean needSelect = tabs.isEmpty();
        if(tabs.containsKey(tab)) {
            components = (VBox) ((ScrollPane)tabs.get(tab).getContent()).getContent();
        } else {
            components = addTab(tab, application.getTranslation(String.format("runtime.scenes.options.tabs.%s", tab), tab));
        }
        components.getChildren().add(vBox);
        if(needSelect) {
            tabPane.getSelectionModel().select(0);
        }
        return checkBox::setSelected;
    }

}
