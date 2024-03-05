package pro.gravit.launcher.gui.impl;

import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launcher.base.profiles.optional.OptionalFile;
import pro.gravit.launcher.base.profiles.optional.OptionalView;
import pro.gravit.launcher.base.profiles.optional.triggers.OptionalTrigger;
import pro.gravit.launcher.base.profiles.optional.triggers.OptionalTriggerContext;
import pro.gravit.utils.helper.JavaHelper;

import java.util.Locale;

public class TriggerManager {
    private final JavaFXApplication application;

    public TriggerManager(JavaFXApplication application) {
        this.application = application;
    }

    public void process(ClientProfile profile, OptionalView view) {
        TriggerManagerContext context = new TriggerManagerContext(profile);
        for (OptionalFile optional : view.all) {
            if (optional.limited) {
                if (!application.authService.checkPermission("launcher.runtime.optionals.%s.%s.show"
                                                          .formatted(profile.getUUID(),
                                                                     optional.name.toLowerCase(Locale.ROOT)))) {
                    view.disable(optional, null);
                    optional.visible = false;
                } else {
                    optional.visible = true;
                }
            }
            if (optional.triggersList == null) continue;
            boolean isRequired = false;
            int success = 0;
            int fail = 0;
            for (OptionalTrigger trigger : optional.triggersList) {
                if (trigger.required) isRequired = true;
                if (trigger.check(optional, context)) {
                    success++;
                } else {
                    fail++;
                }
            }
            if (isRequired) {
                if (fail == 0) view.enable(optional, true, null);
                else view.disable(optional, null);
            } else {
                if (success > 0) view.enable(optional, false, null);
            }
        }
    }

    private class TriggerManagerContext implements OptionalTriggerContext {
        private final ClientProfile profile;

        private TriggerManagerContext(ClientProfile profile) {
            this.profile = profile;
        }

        @Override
        public ClientProfile getProfile() {
            return profile;
        }

        @Override
        public String getUsername() {
            return application.authService.getUsername();
        }

        @Override
        public JavaHelper.JavaVersion getJavaVersion() {
            RuntimeSettings.ProfileSettings profileSettings = application.getProfileSettings(profile);
            for (JavaHelper.JavaVersion version : application.javaService.javaVersions) {
                if (profileSettings.javaPath != null && profileSettings.javaPath.equals(version.jvmDir.toString())) {
                    return version;
                }
            }
            return JavaHelper.JavaVersion.getCurrentJavaVersion();
        }
    }
}
