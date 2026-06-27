package pro.gravit.launcher.gui.scenes.settings.components;

import javafx.application.Platform;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaSelector {

    private static final Logger logger =
            LoggerFactory.getLogger(JavaSelector.class);

    private final ComboBox<LauncherBackendAPI.Java> comboBox;
    private final AtomicInteger requestVersion = new AtomicInteger();
    private LauncherBackendAPI.ClientProfileSettings profileSettings;
    private boolean suppressSelectionEvents;

    public JavaSelector(Pane layout) {
        comboBox = LookupHelper.lookup(layout, "#javaCombo");
        comboBox.getItems().clear();
        comboBox.setOnAction(e -> onSelectionChanged());
    }

    public void reset(LauncherBackendAPI.ClientProfileSettings profileSettings, ProfileFeatureAPI.ClientProfile profile) {
        this.profileSettings = profileSettings;
        comboBox.setConverter(new JavaVersionConverter(profileSettings));
        comboBox.setCellFactory(new JavaVersionCellFactory(comboBox.getConverter()));
        comboBox.getItems().clear();
        comboBox.setDisable(true);
        int currentRequestVersion = requestVersion.incrementAndGet();
        LauncherBackendAPIHolder.getApi().getAvailableJava()
                .whenComplete((result, throwable) -> Platform.runLater(() -> {
                    if (currentRequestVersion != requestVersion.get()) {
                        return;
                    }
                    if (throwable != null) {
                        logger.error("JavaSelector load failed profile={} requestVersion={}",
                                profile.getUUID(), currentRequestVersion, throwable);
                        comboBox.setDisable(false);
                        return;
                    }
                    LoadResult loadResult = prepareLoadResult(this.profileSettings, result);
                    suppressSelectionEvents = true;
                    try {
                        comboBox.getItems().setAll(loadResult.compatibleJavas());
                        comboBox.setValue(loadResult.selectedJava());
                        updateTooltip(loadResult.selectedJava());
                    } finally {
                        suppressSelectionEvents = false;
                    }
                    comboBox.setDisable(false);
                }));
    }

    public String getPath() {
        LauncherBackendAPI.Java value = comboBox.getValue();
        return value == null || value.getPath() == null ? "" : value.getPath().toAbsolutePath().toString();
    }

    private LoadResult prepareLoadResult(LauncherBackendAPI.ClientProfileSettings profileSettings,
            List<LauncherBackendAPI.Java> javas) {
        List<LauncherBackendAPI.Java> compatibleJavas = new ArrayList<>();
        for (LauncherBackendAPI.Java version : javas) {
            if (!profileSettings.isCompatible(version)) {
                continue;
            }
            compatibleJavas.add(version);
        }
        return new LoadResult(compatibleJavas, profileSettings.getSelectedJava());
    }

    private void onSelectionChanged() {
        if (suppressSelectionEvents || profileSettings == null) {
            return;
        }
        LauncherBackendAPI.Java version = comboBox.getValue();
        if (version == null) {
            updateTooltip(null);
            return;
        }
        profileSettings.setSelectedJava(version);
        logger.info("Select Java {}", version.getPath().toAbsolutePath());
        updateTooltip(version);
    }

    private void updateTooltip(LauncherBackendAPI.Java version) {
        if (comboBox.getTooltip() == null) {
            return;
        }
        String tooltipText = version == null || version.getPath() == null
                ? ""
                : version.getPath().toAbsolutePath().toString();
        comboBox.getTooltip().setText(tooltipText);
    }

    private record LoadResult(List<LauncherBackendAPI.Java> compatibleJavas,
                              LauncherBackendAPI.Java selectedJava) {
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
