package pro.gravit.launcher.gui.scenes.settings;

import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import oshi.SystemInfo;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.components.UserBlock;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.components.ServerButton;
import pro.gravit.launcher.gui.scenes.interfaces.SceneSupportUserBlock;
import pro.gravit.launcher.gui.scenes.settings.components.JavaSelectorComponent;
import pro.gravit.launcher.gui.utils.SystemMemory;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.utils.helper.JVMHelper;

import java.text.MessageFormat;

public class SettingsScene extends BaseSettingsScene implements SceneSupportUserBlock {

    private final static long MAX_JAVA_MEMORY_X64 = 32 * 1024;
    private final static long MAX_JAVA_MEMORY_X32 = 1536;
    private Label ramLabel;
    private Slider ramSlider;
    private RuntimeSettings.ProfileSettingsView profileSettings;
    private JavaSelectorComponent javaSelector;
    private UserBlock userBlock;

    public SettingsScene(JavaFXApplication application) {
        super("scenes/settings/settings.fxml", application);
    }

    @Override
    protected void doInit() {
        super.doInit();
        this.userBlock = new UserBlock(layout, new SceneAccessor());

        ramSlider = LookupHelper.lookup(componentList, "#ramSlider");
        ramLabel = LookupHelper.lookup(componentList, "#ramLabel");
        long maxSystemMemory;
        try {
            SystemInfo systemInfo = new SystemInfo();
            maxSystemMemory = (systemInfo.getHardware().getMemory().getTotal() >> 20);
        } catch (Throwable ignored) {
            try {
                maxSystemMemory = (SystemMemory.getPhysicalMemorySize() >> 20);
            } catch (Throwable ignored1) {
                maxSystemMemory = 2048;
            }
        }
        ramSlider.setMax(Math.min(maxSystemMemory, getJavaMaxMemory()));

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(1);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
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
        reset();
    }

    private long getJavaMaxMemory() {
        if (application.javaService.isArchAvailable(JVMHelper.ARCH.X86_64) || application.javaService.isArchAvailable(
                JVMHelper.ARCH.ARM64)) {
            return MAX_JAVA_MEMORY_X64;
        }
        return MAX_JAVA_MEMORY_X32;
    }

    @Override
    public void reset() {
        super.reset();
        profileSettings = new RuntimeSettings.ProfileSettingsView(application.getProfileSettings());
        javaSelector = new JavaSelectorComponent(application.javaService, componentList, profileSettings,
                                                 application.profilesService.getProfile());
        ramSlider.setValue(profileSettings.ram);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            profileSettings.ram = newValue.intValue();
            updateRamLabel();
        });
        updateRamLabel();
        Pane serverButtonContainer = LookupHelper.lookup(layout, "#serverButton");
        serverButtonContainer.getChildren().clear();
        ClientProfile profile = application.profilesService.getProfile();
        ServerButton serverButton = ServerButton.createServerButton(application, profile);
        serverButton.addTo(serverButtonContainer);
        serverButton.enableSaveButton(null, (e) -> {
            try {
                profileSettings.apply();
                application.triggerManager.process(profile, application.profilesService.getOptionalView());
                switchToBackScene();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        });
        serverButton.enableResetButton(null, (e) -> reset());
        add("Debug", application.runtimeSettings.globalSettings.debugAllClients || profileSettings.debug, (value) -> profileSettings.debug = value, application.runtimeSettings.globalSettings.debugAllClients);
        add("AutoEnter", profileSettings.autoEnter, (value) -> profileSettings.autoEnter = value, false);
        add("Fullscreen", profileSettings.fullScreen, (value) -> profileSettings.fullScreen = value, false);
        if(JVMHelper.OS_TYPE == JVMHelper.OS.LINUX) {
            add("WaylandSupport", profileSettings.waylandSupport, (value) -> profileSettings.waylandSupport = value, false);
        }
        if(application.authService.checkDebugPermission("skipupdate")) {
            add("DebugSkipUpdate", profileSettings.debugSkipUpdate, (value) -> profileSettings.debugSkipUpdate = value, false);
        }
        if(application.authService.checkDebugPermission("skipfilemonitor")) {
            add("DebugSkipFileMonitor", profileSettings.debugSkipFileMonitor, (value) -> profileSettings.debugSkipFileMonitor = value, false);
        }
        userBlock.reset();
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
        ramLabel.setText(profileSettings.ram == 0
                                 ? application.getTranslation("runtime.scenes.settings.ramAuto")
                                 : MessageFormat.format(application.getTranslation("runtime.scenes.settings.ram"),
                                                        profileSettings.ram));
    }
}
