package pro.gravit.launcher.gui.scenes.settings.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;

import java.util.Objects;

public class ThemeSelector {

    private static final Logger logger =
            LoggerFactory.getLogger(ThemeSelector.class);

    private final JavaFXApplication application;
    private final ComboBox<RuntimeSettings.LAUNCHER_THEME> comboBox;

    public ThemeSelector(JavaFXApplication application, Pane layout) {
        this.application = application;
        comboBox = LookupHelper.lookup(layout, ComboBox.class, "#themeCombo");
        comboBox.getItems().clear();
        comboBox.setConverter(new ThemeConverter());
        for(var e : RuntimeSettings.LAUNCHER_THEME.values()) {
            comboBox.getItems().add(e);
        }
        comboBox.getSelectionModel().select(Objects.requireNonNullElse(application.runtimeSettings.theme,
                                                                       RuntimeSettings.LAUNCHER_THEME.COMMON));
        comboBox.setOnAction(e -> {
            RuntimeSettings.LAUNCHER_THEME theme = comboBox.getValue();
            if (theme == null || (theme == RuntimeSettings.LAUNCHER_THEME.COMMON && application.runtimeSettings.theme == null)) return;
            if(theme == application.runtimeSettings.theme) return;
            if (application.isOperationBusy()) { comboBox.setValue(application.runtimeSettings.theme); return; }
            var previous = application.runtimeSettings.theme;
            application.runtimeSettings.theme = theme;
            try {
                application.gui.reload();
            } catch (Exception ex) {
                application.runtimeSettings.theme = previous;
                comboBox.setValue(previous);
                logger.error("", ex);
            }
        });
    }

    private class ThemeConverter extends StringConverter<RuntimeSettings.LAUNCHER_THEME> {

        @Override
        public String toString(RuntimeSettings.LAUNCHER_THEME object) {
            if (object == null) return "Unknown";
            return application.getTranslation(String.format("runtime.themes.%s", object.displayName), object.displayName);
        }

        @Override
        public RuntimeSettings.LAUNCHER_THEME fromString(String string) {
            return null;
        }
    }
}