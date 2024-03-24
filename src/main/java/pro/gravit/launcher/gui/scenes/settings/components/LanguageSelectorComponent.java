package pro.gravit.launcher.gui.scenes.settings.components;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.utils.helper.LogHelper;

import java.util.Objects;

public class LanguageSelectorComponent {
    private final JavaFXApplication application;
    private final ComboBox<RuntimeSettings.LAUNCHER_LOCALE> comboBox;

    public LanguageSelectorComponent(JavaFXApplication application, Pane layout) {
        this.application = application;
        comboBox = LookupHelper.lookup(layout, "#languageCombo");
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
                application.updateLocaleResources(locale.name);
                application.runtimeSettings.locale = locale;
                application.gui.reload();
            } catch (Exception ex) {
                LogHelper.error(ex);
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
