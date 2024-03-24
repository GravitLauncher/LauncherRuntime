package pro.gravit.launcher.gui.service;

import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.runtime.client.DirBridge;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.JavaHelper;
import pro.gravit.utils.helper.LogHelper;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaService {
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile(
            "Java (?<version>.+) b(?<build>.+) (?<os>.+) (?<arch>.+) javafx (?<javafx>.+)");
    public volatile List<JavaHelper.JavaVersion> javaVersions;
    private final JavaFXApplication application;

    public JavaService(JavaFXApplication application) {
        this.application = application;
        update();
    }

    public void update() {
        List<JavaHelper.JavaVersion> versions = new LinkedList<>();
        {
            if (application.guiModuleConfig.javaList != null) {
                for (Map.Entry<String, String> entry : application.guiModuleConfig.javaList.entrySet()) {
                    String javaDir = entry.getKey();
                    String javaVersionString = entry.getValue();
                    Matcher matcher = JAVA_VERSION_PATTERN.matcher(javaVersionString);
                    if (matcher.matches()) {
                        String os = matcher.group("os");
                        int version = Integer.parseInt(matcher.group("version"));
                        int build = Integer.parseInt(matcher.group("build"));
                        JVMHelper.ARCH arch = JVMHelper.ARCH.valueOf(matcher.group("arch"));
                        boolean javafx = Boolean.parseBoolean(matcher.group("javafx"));
                        if (!isArchAvailable(arch)) {
                            continue;
                        }
                        if (!JVMHelper.OS_TYPE.name.equals(os)) {
                            continue;
                        }
                        Path javaDirectory = DirBridge.dirUpdates.resolve(javaDir);
                        LogHelper.debug("In-Launcher Java Version found: Java %d b%d %s javafx %s", version, build,
                                        arch.name, Boolean.toString(javafx));
                        JavaHelper.JavaVersion javaVersion = new JavaHelper.JavaVersion(javaDirectory, version, build,
                                                                                        arch, javafx);
                        versions.add(javaVersion);
                    } else {
                        LogHelper.warning("Java Version: %s does not match", javaVersionString);
                    }
                }
            }
        }
        if (!application.guiModuleConfig.forceDownloadJava || versions.isEmpty()) {
            versions.addAll(JavaHelper.findJava());
        }
        javaVersions = Collections.unmodifiableList(versions);

    }

    public boolean isArchAvailable(JVMHelper.ARCH arch) {
        if (JVMHelper.ARCH_TYPE == arch) {
            return true;
        }
        if (arch == JVMHelper.ARCH.X86_64 && JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE
                && ((JVMHelper.ARCH_TYPE == JVMHelper.ARCH.X86 && !JVMHelper.isJVMMatchesSystemArch())
                || JVMHelper.ARCH_TYPE == JVMHelper.ARCH.ARM64)) {
            return true;
        }
        return arch == JVMHelper.ARCH.X86_64
                && JVMHelper.OS_TYPE == JVMHelper.OS.MACOSX
                && JVMHelper.ARCH_TYPE == JVMHelper.ARCH.ARM64;
    }

    public boolean isIncompatibleJava(JavaHelper.JavaVersion version, ClientProfile profile) {
        return version.version > profile.getMaxJavaVersion() || version.version < profile.getMinJavaVersion();
    }

    public boolean contains(Path dir) {
        for (JavaHelper.JavaVersion version : javaVersions) {
            if (version.jvmDir.toAbsolutePath().equals(dir.toAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    public JavaHelper.JavaVersion getRecommendJavaVersion(ClientProfile profile) {
        int min = profile.getMinJavaVersion();
        int max = profile.getMaxJavaVersion();
        int recommend = profile.getRecommendJavaVersion();
        JavaHelper.JavaVersion result = null;
        for (JavaHelper.JavaVersion version : javaVersions) {
            if (version.version < min || version.version > max) continue;
            if (isIncompatibleJava(version, profile)) {
                continue;
            }
            if (result == null) {
                result = version;
                continue;
            }
            if (result.version != recommend && version.version == recommend) {
                result = version;
                continue;
            }
            if ((result.version == recommend) == (version.version == recommend)) {
                if (result.version < version.version) {
                    result = version;
                    continue;
                }
                if ((result.arch == JVMHelper.ARCH.X86 && version.arch == JVMHelper.ARCH.X86_64)
                        || (result.arch == JVMHelper.ARCH.X86_64 && version.arch == JVMHelper.ARCH.ARM64)) {
                    result = version;
                }
                if (result.version == version.version && result.build < version.build) {
                    result = version;
                }
            }
        }
        return result;
    }
}
