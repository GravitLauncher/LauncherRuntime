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

public class LanguageSelector {

    private static final Logger logger =
            LoggerFactory.getLogger(LanguageSelector.class);

    private final JavaFXApplication application;
    private final ComboBox<RuntimeSettings.LAUNCHER_LOCALE> comboBox;

    public LanguageSelector(JavaFXApplication application, Pane layout) {
        this.application = application;
        comboBox = LookupHelper.lookup(layout, ComboBox.class, "#languageCombo");
        comboBox.getItems().clear();
        comboBox.setConverter(new ThemeConverter());
        for(var e : RuntimeSettings.LAUNCHER_LOCALE.values()) {
            comboBox.getItems().add(e);
        }
        comboBox.getSelectionModel().select(Objects.requireNonNullElse(application.runtimeSettings.locale,
                                                                       RuntimeSettings.LAUNCHER_LOCALE.ENGLISH));
        comboBox.setOnAction(e -> {
            RuntimeSettings.LAUNCHER_LOCALE locale = comboBox.getValue();
            if (locale == null) return;
            if(locale == application.runtimeSettings.locale) return;
            try {
                if (application.isOperationBusy()) { comboBox.setValue(application.runtimeSettings.locale); return; }
                var oldResources = application.captureResources();
                var previous = application.runtimeSettings.locale;
                application.runtimeSettings.locale = locale;
                try {
                    application.gui.reload();
                } catch (Exception error) {
                    application.runtimeSettings.locale = previous;
                    application.restoreResources(oldResources);
                    comboBox.setValue(previous);
                    throw error;
                }
            } catch (Exception ex) {
                logger.error("", ex);
            }
        });
    }

    private class ThemeConverter extends StringConverter<RuntimeSettings.LAUNCHER_LOCALE> {

        @Override
        public String toString(RuntimeSettings.LAUNCHER_LOCALE object) {
            if (object == null) return "Unknown";
            return application.getTranslation(String.format("runtime.themes.%s", object.displayName), object.displayName);
        }

        @Override
        public RuntimeSettings.LAUNCHER_LOCALE fromString(String string) {
            return null;
        }
    }
}