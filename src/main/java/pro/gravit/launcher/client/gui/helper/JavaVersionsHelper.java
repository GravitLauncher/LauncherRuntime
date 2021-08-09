package pro.gravit.launcher.client.gui.helper;

import pro.gravit.launcher.ClientLauncherWrapper;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.JavaHelper;

import java.util.Collections;
import java.util.List;

public class JavaVersionsHelper {
    public static final List<JavaHelper.JavaVersion> javaVersions = Collections.unmodifiableList(JavaHelper.findJava());
    public static JavaHelper.JavaVersion getRecommendJavaVersion(ClientProfile profile) {
        int min = profile.getMinJavaVersion();
        int max = profile.getMaxJavaVersion();
        int recommend = profile.getRecommendJavaVersion();
        JavaHelper.JavaVersion result = null;
        for(JavaHelper.JavaVersion version : javaVersions) {
            if(version.version < min || version.version > max) continue;
            if(result == null) {
                result = version;
                continue;
            }
            if(result.version != recommend && version.version == recommend) {
                result = version;
                continue;
            }
            if((result.version == recommend) == (version.version == recommend) ) {
                if(result.version < version.version) {
                    result = version;
                    continue;
                }
                if(result.version == version.version && result.build < version.build) {
                    result = version;
                }
            }
        }
        return result;
    }
}
