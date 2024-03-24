package pro.gravit.launcher.gui.scenes.settings.components;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.utils.helper.LogHelper;

import java.util.Objects;

public class ThemeSelectorComponent {
    private final JavaFXApplication application;
    private final ComboBox<RuntimeSettings.LAUNCHER_THEME> comboBox;

    public ThemeSelectorComponent(JavaFXApplication application, Pane layout) {
        this.application = application;
        comboBox = LookupHelper.lookup(layout, "#themeCombo");
        comboBox.getItems().clear();
        comboBox.setConverter(new ThemeConverter());
        if(application.isThemeSupport()) {
            for(var e : RuntimeSettings.LAUNCHER_THEME.values()) {
                comboBox.getItems().add(e);
            }
        } else {
            comboBox.getItems().add(RuntimeSettings.LAUNCHER_THEME.COMMON);
        }
        comboBox.getSelectionModel().select(Objects.requireNonNullElse(application.runtimeSettings.theme,
                                                                       RuntimeSettings.LAUNCHER_THEME.COMMON));
        comboBox.setOnAction(e -> {
            RuntimeSettings.LAUNCHER_THEME theme = comboBox.getValue();
            if (theme == null || (theme == RuntimeSettings.LAUNCHER_THEME.COMMON && application.runtimeSettings.theme == null)) return;
            if(theme == application.runtimeSettings.theme) return;
            application.runtimeSettings.theme = theme;
            try {
                application.gui.reload();
            } catch (Exception ex) {
                LogHelper.error(ex);
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
