package pro.gravit.launcher.client.gui.scenes.settings;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.service.JavaService;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.JavaHelper;

public class JavaSelectorComponent {
    private final ComboBox<JavaHelper.JavaVersion> comboBox;
    private final Label javaPath;
    private final Label javaError;
    private final RuntimeSettings.ProfileSettingsView profileSettings;
    private final ClientProfile profile;

    public JavaSelectorComponent(JavaService javaService, Pane layout, RuntimeSettings.ProfileSettingsView profileSettings, ClientProfile profile) {
        comboBox = LookupHelper.lookup(layout, "#javaCombo");
        this.profile = profile;
        comboBox.getItems().clear();
        javaPath = LookupHelper.lookup(layout, "#javaPath");
        javaError = LookupHelper.lookup(layout, "#javaError");
        this.profileSettings = profileSettings;
        comboBox.setConverter(new JavaVersionConverter(profile));
        comboBox.setOnAction(e -> {
            JavaHelper.JavaVersion version = comboBox.getValue();
            if (version == null) return;
            javaPath.setText(version.jvmDir.toAbsolutePath().toString());
            profileSettings.javaPath = javaPath.getText();
            javaError.setVisible(javaService.isIncompatibleJava(version, profile));
        });
        for (JavaHelper.JavaVersion version : javaService.javaVersions) {
            comboBox.getItems().add(version);
            if (profileSettings.javaPath != null && profileSettings.javaPath.equals(version.jvmDir.toString())) {
                comboBox.setValue(version);
            }
        }
        JavaHelper.JavaVersion recommend = javaService.getRecommendJavaVersion(profile);
        if (recommend != null) {
            comboBox.getSelectionModel().select(recommend);
        }
    }

    public String getPath() {
        return comboBox.getValue().jvmDir.toAbsolutePath().toString();
    }

    private static class JavaVersionConverter extends StringConverter<JavaHelper.JavaVersion> {
        private final ClientProfile profile;

        private JavaVersionConverter(ClientProfile profile) {
            this.profile = profile;
        }

        @Override
        public String toString(JavaHelper.JavaVersion object) {
            if (object == null) return "Unknown";
            String postfix = "";
            if (object.version == profile.getRecommendJavaVersion()) {
                postfix = "[RECOMMENDED]";
            }
            return String.format("Java %d b%d %s", object.version, object.build, postfix);
        }

        @Override
        public JavaHelper.JavaVersion fromString(String string) {
            return null;
        }
    }
}
