package pro.gravit.launcher.client.gui.helper;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.utils.RuntimeCryptedFile;
import pro.gravit.utils.enfs.EnFS;
import pro.gravit.utils.enfs.dir.FileEntry;
import pro.gravit.utils.enfs.dir.URLFile;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EnFSHelper {

    public static boolean checkEnFSUrl() {
        try {
            URL url = new URL(new URL("enfs", null, -1, "aone").toString()); // check URL Handler
            return true;
        } catch (IOException e) {
            return false; // Failed on Java 8
        }
    }

    private static void initEnFS() {
        if(JVMHelper.JVM_VERSION == 8) {
            // Java 8 not supported `java.net.spi.URLStreamHandlerProvider`
            LogHelper.info("Java pkgs: %s", System.getProperty("java.protocol.handler.pkgs"));
            // Format: {PKG}.{PROTOCOL}.Handler
            // Result class: pro.gravit.util.enfs.protocol.enfs.Handler
            System.setProperty("java.protocol.handler.pkgs", "pro.gravit.utils.enfs.protocol");
        }
    }

    public static Path initEnFS(LauncherConfig config) throws IOException {
        initEnFS();
        Path enfsDirectory = Paths.get("aone");
        EnFS.main.newDirectory(enfsDirectory);
        config.runtime.forEach((name, digest) -> {
            EnFS.main.newDirectories(enfsDirectory.resolve(name).getParent());
            try {
                FileEntry entry;
                if(config.runtimeEncryptKey == null) {
                    entry = new URLFile(Launcher.getResourceURL(name));
                } else {
                    String encodedName = "runtime/"+ SecurityHelper.toHex(digest);
                    entry = new RuntimeCryptedFile(() -> {
                        try {
                            InputStream inputStream = IOHelper.newInput(IOHelper.getResourceURL(encodedName));
                            return inputStream;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, SecurityHelper.fromHex(config.runtimeEncryptKey));
                }
                EnFS.main.addFile(enfsDirectory.resolve(name), entry);
                LogHelper.debug("Pushed %s", enfsDirectory.resolve(name).toString());
            } catch (Exception e) {
                LogHelper.error(e);
            }
        });
        return enfsDirectory;
    }

    public static URL getURL(String name) throws IOException {
        try(InputStream stream = EnFS.main.getInputStream(Paths.get(name))) {
            return new URL("enfs", null, -1, name);
        } catch (UnsupportedOperationException ex) {
            throw new FileNotFoundException(name);
        }
    }
}
