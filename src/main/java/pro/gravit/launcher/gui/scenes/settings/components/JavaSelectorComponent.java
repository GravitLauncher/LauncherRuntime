package pro.gravit.launcher.gui.scenes.settings.components;

import javafx.scene.control.*;
import javafx.scene.layout.Pane;

import javafx.util.Callback;
import javafx.util.StringConverter;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.service.JavaService;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.utils.helper.JavaHelper;
import pro.gravit.utils.helper.LogHelper;

public class JavaSelectorComponent {
    private final ComboBox<JavaHelper.JavaVersion> comboBox;
    private final RuntimeSettings.ProfileSettingsView profileSettings;
    private final ClientProfile profile;
    private final JavaService javaService;

    public JavaSelectorComponent(JavaService javaService, Pane layout,
            RuntimeSettings.ProfileSettingsView profileSettings, ClientProfile profile) {
        comboBox = LookupHelper.lookup(layout, "#javaCombo");
        this.profile = profile;
        comboBox.getItems().clear();
        this.profileSettings = profileSettings;
        this.javaService = javaService;
        comboBox.setConverter(new JavaVersionConverter(profile));
        comboBox.setCellFactory(new JavaVersionCellFactory(comboBox.getConverter()));
        reset();
    }

    public void reset() {
        boolean reset = true;
        for (JavaHelper.JavaVersion version : javaService.javaVersions) {
            if (javaService.isIncompatibleJava(version, profile)) {
                continue;
            }
            comboBox.getItems().add(version);
            if (profileSettings.javaPath != null && profileSettings.javaPath.equals(version.jvmDir.toString())) {
                comboBox.setValue(version);
                reset = false;
            }
        }
        if (comboBox.getTooltip() != null) {
            comboBox.getTooltip().setText(profileSettings.javaPath);
        }
        if (reset) {
            JavaHelper.JavaVersion recommend = javaService.getRecommendJavaVersion(profile);
            if (recommend != null) {
                LogHelper.warning("Selected Java Version not found. Using %s",
                                  recommend.jvmDir.toAbsolutePath().toString());
                comboBox.getSelectionModel().select(recommend);
                profileSettings.javaPath = recommend.jvmDir.toAbsolutePath().toString();
            }
        }
        comboBox.setOnAction(e -> {
            JavaHelper.JavaVersion version = comboBox.getValue();
            if (version == null) return;
            var path = version.jvmDir.toAbsolutePath().toString();
            if (comboBox.getTooltip() != null) {
                comboBox.getTooltip().setText(path);
            }
            LogHelper.info("Select Java %s", path);
            profileSettings.javaPath = path;
        });
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
            return "Java %d b%d %s".formatted(object.version, object.build, postfix);
        }

        @Override
        public JavaHelper.JavaVersion fromString(String string) {
            return null;
        }
    }

    private static class JavaVersionCellFactory implements Callback<ListView<JavaHelper.JavaVersion>, ListCell<JavaHelper.JavaVersion>> {

        private final StringConverter<JavaHelper.JavaVersion> converter;

        public JavaVersionCellFactory(StringConverter<JavaHelper.JavaVersion> converter) {
            this.converter = converter;
        }

        @Override
        public ListCell<JavaHelper.JavaVersion> call(ListView<JavaHelper.JavaVersion> param) {
            return new JavaVersionListCell(converter);
        }
    }

    private static class JavaVersionListCell extends ListCell<JavaHelper.JavaVersion> {
        private final StringConverter<JavaHelper.JavaVersion> converter;

        public JavaVersionListCell(StringConverter<JavaHelper.JavaVersion> converter) {
            this.converter = converter;
        }

        @Override
        protected void updateItem(JavaHelper.JavaVersion item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setTooltip(null);
            } else {
                setText(converter.toString(item));
                Tooltip tooltip = new Tooltip(item.jvmDir.toString());
                tooltip.setAnchorLocation(Tooltip.AnchorLocation.WINDOW_BOTTOM_LEFT);
                setTooltip(tooltip);
            }
        }
    }
}
