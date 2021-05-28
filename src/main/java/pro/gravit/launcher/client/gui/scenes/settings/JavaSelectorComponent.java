package pro.gravit.launcher.client.gui.scenes.settings;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.ClientLauncherWrapper;
import pro.gravit.launcher.client.gui.config.RuntimeSettings;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JavaSelectorComponent {
    private final ComboBox<ClientLauncherWrapper.JavaVersion> comboBox;
    private final List<String> javaPaths = new ArrayList<>();
    private final Label javaPath;
    private final RuntimeSettings.ProfileSettingsView profileSettings;

    public JavaSelectorComponent(Pane layout, RuntimeSettings.ProfileSettingsView profileSettings) {
        comboBox = LookupHelper.lookup(layout, "#javaCombo");
        comboBox.getItems().clear();
        javaPath = LookupHelper.lookup(layout, "#javaPath");
        this.profileSettings = profileSettings;
        comboBox.setConverter(new JavaVersionConverter());
        comboBox.setOnAction(e -> {
            if(comboBox.getValue() == null) return;
            javaPath.setText(comboBox.getValue().jvmDir.toAbsolutePath().toString());
            profileSettings.javaPath = javaPath.getText();
        });
        findJava();
    }

    public String getPath() {
        return comboBox.getValue().jvmDir.toAbsolutePath().toString();
    }

    public void findJava() {
        try {
            tryAddJava(ClientLauncherWrapper.JavaVersion.getCurrentJavaVersion());
        } catch (IOException e) {
            LogHelper.error(e);
        }
        String[] path = System.getenv("PATH").split(JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE ? ";" : ":");
        for(String p : path) {
            try {
                Path p1 = Paths.get(p);
                Path javaExecPath = JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE ? p1.resolve("java.exe") : p1.resolve("java");
                if(Files.exists(javaExecPath)) {
                    if(Files.isSymbolicLink(javaExecPath)) {
                        javaExecPath = javaExecPath.toRealPath();
                    }
                    p1 = javaExecPath.getParent().getParent();
                    tryAddJava(ClientLauncherWrapper.JavaVersion.getByPath(p1));
                    trySearchJava(p1.getParent());
                }
            } catch (IOException e){
                LogHelper.error(e);
            }
        }
        if(JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE) {
            Path rootDrive = Paths.get(System.getProperty("java.home"));
            try {
                trySearchJava(rootDrive.resolve("Program Files").resolve("Java"));
                trySearchJava(rootDrive.resolve("Program Files").resolve("AdoptOpenJDK"));
            } catch (IOException e) {
                LogHelper.error(e);
            }
        }
        else if(JVMHelper.OS_TYPE == JVMHelper.OS.LINUX) {
            try {
                trySearchJava(Paths.get("/usr/lib/jvm"));
            } catch (IOException e) {
                LogHelper.error(e);
            }
        }
    }

    public void tryAddJava(ClientLauncherWrapper.JavaVersion version) throws IOException {
        if(version == null) return;
        String path = version.jvmDir.toAbsolutePath().toString();
        if(javaPaths.contains(path)) return;
        comboBox.getItems().add(version);
        javaPaths.add(path);
        if(path.equals(profileSettings.javaPath)) {
            comboBox.getSelectionModel().select(version);
        }
    }

    public void trySearchJava(Path path) throws IOException {
        if(!Files.isDirectory(path)) return;
        Files.list(path).filter(p -> Files.exists(p.resolve("bin").resolve(JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE ? "java.exe" : "java"))).forEach(e -> {
            try {
                tryAddJava(ClientLauncherWrapper.JavaVersion.getByPath(e));
                if(Files.exists(e.resolve("jre"))) {
                    tryAddJava(ClientLauncherWrapper.JavaVersion.getByPath(e.resolve("jre")));
                }
            } catch (IOException ioException) {
                LogHelper.error(ioException);
            }
        });
    }

    private static class JavaVersionConverter extends StringConverter<ClientLauncherWrapper.JavaVersion> {

        @Override
        public String toString(ClientLauncherWrapper.JavaVersion object) {
            if(object == null) return "Unknown";
            return String.format("Java %d b%d", object.version, object.build);
        }

        @Override
        public ClientLauncherWrapper.JavaVersion fromString(String string) {
            return null;
        }
    }
}
