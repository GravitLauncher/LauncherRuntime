package pro.gravit.launcher.client.gui.helper;

import pro.gravit.launcher.ClientLauncherWrapper;
import pro.gravit.launcher.profiles.ClientProfile;

import java.util.Collections;
import java.util.List;

public class JavaVersionsHelper {
    public static final List<ClientLauncherWrapper.JavaVersion> javaVersions = Collections.unmodifiableList(ClientLauncherWrapper.findJava());
    public static ClientLauncherWrapper.JavaVersion getRecommendJavaVersion(ClientProfile profile) {
        int min = profile.getMinJavaVersion();
        int max = profile.getMaxJavaVersion();
        int recommend = profile.getRecommendJavaVersion();
        ClientLauncherWrapper.JavaVersion result = null;
        for(ClientLauncherWrapper.JavaVersion version : javaVersions) {
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
