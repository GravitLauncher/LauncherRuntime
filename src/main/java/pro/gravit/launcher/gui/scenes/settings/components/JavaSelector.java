package pro.gravit.launcher.gui.scenes.settings.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.util.Callback;
import javafx.util.StringConverter;
import pro.gravit.launcher.core.api.features.ProfileFeatureAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.helper.LookupHelper;

public class JavaSelector {

    private static final Logger logger =
            LoggerFactory.getLogger(JavaSelector.class);

    private final ComboBox<LauncherBackendAPI.Java> comboBox;
    private final LauncherBackendAPI.ClientProfileSettings profileSettings;
    private final ProfileFeatureAPI.ClientProfile profile;

    public JavaSelector(Pane layout,
            LauncherBackendAPI.ClientProfileSettings profileSettings, ProfileFeatureAPI.ClientProfile profile) {
        comboBox = LookupHelper.lookup(layout, "#javaCombo");
        this.profile = profile;
        comboBox.getItems().clear();
        this.profileSettings = profileSettings;
        comboBox.setConverter(new JavaVersionConverter(profileSettings));
        comboBox.setCellFactory(new JavaVersionCellFactory(comboBox.getConverter()));
        reset();
    }

    public void reset() {
        boolean reset = true;
        LauncherBackendAPIHolder.getApi().getAvailableJava().thenAccept((javas) -> {
            for (LauncherBackendAPI.Java version : javas) {
                if (!profileSettings.isCompatible(version)) {
                    continue;
                }
                comboBox.getItems().add(version);
            }
            comboBox.setValue(profileSettings.getSelectedJava());
            if (comboBox.getTooltip() != null && profileSettings.getSelectedJava() != null) {
                comboBox.getTooltip().setText(profileSettings.getSelectedJava().getPath().toAbsolutePath().toString());
            }
            comboBox.setOnAction(e -> {
                LauncherBackendAPI.Java version = comboBox.getValue();
                if (version == null) return;
                logger.info("Select Java {}", version.getPath().toAbsolutePath().toString());
                profileSettings.setSelectedJava(version);
            });
        });
    }

    public String getPath() {
        return comboBox.getValue().getPath().toAbsolutePath().toString();
    }

    private static class JavaVersionConverter extends StringConverter<LauncherBackendAPI.Java> {
        private final LauncherBackendAPI.ClientProfileSettings settings;

        public JavaVersionConverter(LauncherBackendAPI.ClientProfileSettings settings) {
            this.settings = settings;
        }

        @Override
        public String toString(LauncherBackendAPI.Java object) {
            if (object == null) return "Unknown";
            String postfix = "";
            if (settings.isRecommended(object)) {
                postfix = "[RECOMMENDED]";
            }
            return "Java %d %s %s".formatted(object.getMajorVersion(), object.getArchitecture(), postfix);
        }

        @Override
        public LauncherBackendAPI.Java fromString(String string) {
            return null;
        }
    }

    private static class JavaVersionCellFactory implements Callback<ListView<LauncherBackendAPI.Java>, ListCell<LauncherBackendAPI.Java>> {

        private final StringConverter<LauncherBackendAPI.Java> converter;

        public JavaVersionCellFactory(StringConverter<LauncherBackendAPI.Java> converter) {
            this.converter = converter;
        }

        @Override
        public ListCell<LauncherBackendAPI.Java> call(ListView<LauncherBackendAPI.Java> param) {
            return new JavaVersionListCell(converter);
        }
    }

    private static class JavaVersionListCell extends ListCell<LauncherBackendAPI.Java> {
        private final StringConverter<LauncherBackendAPI.Java> converter;

        public JavaVersionListCell(StringConverter<LauncherBackendAPI.Java> converter) {
            this.converter = converter;
        }

        @Override
        protected void updateItem(LauncherBackendAPI.Java item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setTooltip(null);
            } else {
                setText(converter.toString(item));
                Tooltip tooltip = new Tooltip(item.getPath().toString());
                tooltip.setAnchorLocation(Tooltip.AnchorLocation.WINDOW_BOTTOM_LEFT);
                setTooltip(tooltip);
            }
        }
    }
}