package pro.gravit.launcher.gui.scenes.settings;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.launcher.gui.scenes.settings.components.JavaSelector;

import java.text.MessageFormat;

public class SettingsScene extends BaseSettingsScene implements SceneSupportUserBlock {
    private final static long MAX_JAVA_MEMORY_X64 = 32 * 1024;
    private final static long MAX_JAVA_MEMORY_X32 = 1536;
    private Label ramLabel;
    private Slider ramSlider;
    private LauncherBackendAPI.ClientProfileSettings profileSettings;
    private JavaSelector javaSelector;
    private UserBlock userBlock;
    private boolean ignoreRamSliderChanges;
    private Long cachedMaxMemoryMbs;

    public SettingsScene(JavaFXApplication application) {
        super("scenes/settings/settings.fxml", application);
    }

    @Override
    protected void doInit() {
        super.doInit();
        this.userBlock = use(layout, UserBlock::new);

        ramSlider = LookupHelper.lookup(componentList, "#ramSlider");
        ramLabel = LookupHelper.lookup(componentList, "#ramLabel");

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(1);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (profileSettings == null || ignoreRamSliderChanges) {
                return;
            }
            profileSettings.setReservedMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL,
                    (long) newValue.intValue() << 20);
            updateRamLabel();
        });
        ramSlider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double object) {
                return "%.0fG".formatted(object / 1024);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        LookupHelper.<ButtonBase>lookupIfPossible(layout, "#back").ifPresent(a -> a.setOnAction((e) -> {
            try {
                profileSettings = null;
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        isResetOnShow = true;
    }

    @Override
    public void reset() {
        ProfileFeatureAPI.ClientProfile profile = application.profileService.getCurrentProfile();
        if (profile == null) {
            return;
        }
        applySceneState(profile, true);
    }

    private void applySceneState(ProfileFeatureAPI.ClientProfile profile, boolean refreshStaticBlocks) {
        super.reset();

        profileSettings = LauncherBackendAPIHolder.getApi().makeClientProfileSettings(profile);

        if (javaSelector == null) {
            javaSelector = new JavaSelector(componentList);
        }
        javaSelector.reset(profileSettings, profile);

        ignoreRamSliderChanges = true;
        try {
            ramSlider.setMax(getCachedMaxMemoryMbs());
            ramSlider.setValue(getReservedMemoryMbs());
        } finally {
            ignoreRamSliderChanges = false;
        }
        updateRamLabel();

        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        application.serverButtonCacheService.attachDetailButton(profile, serverButtonContainer, null, e -> {
            try {
                LauncherBackendAPIHolder.getApi().saveClientProfileSettings(profileSettings);
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }, null, e -> {
            applySceneState(profile, false);
        }, "settings");

        for(var flag : profileSettings.getAvailableFlags()) {
            add(flag.name(), profileSettings.hasFlag(flag), (value) -> {
                if(value) {
                    profileSettings.addFlag(flag);
                } else {
                    profileSettings.removeFlag(flag);
                }
            }, false);
        }

        if (refreshStaticBlocks) {
            userBlock.reset();
        }
    }

    private long getReservedMemoryMbs() {
        return profileSettings.getReservedMemoryBytes(LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL) >> 20;
    }

    private long getCachedMaxMemoryMbs() {
        if (cachedMaxMemoryMbs == null) {
            cachedMaxMemoryMbs = profileSettings.getMaxMemoryBytes(
                    LauncherBackendAPI.ClientProfileSettings.MemoryClass.TOTAL) >> 20;
        }
        return cachedMaxMemoryMbs;
    }

    @Override
    public UserBlock getUserBlock() {
        return userBlock;
    }

    @Override
    public String getName() {
        return "settings";
    }

    public void updateRamLabel() {
        ramLabel.setText(getReservedMemoryMbs() == 0
                                 ? application.getTranslation("runtime.scenes.settings.ramAuto")
                                 : MessageFormat.format(application.getTranslation("runtime.scenes.settings.ram"),
                                                        getReservedMemoryMbs()));
    }
}
