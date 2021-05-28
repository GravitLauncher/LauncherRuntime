package pro.gravit.launcher.client.gui.helper;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.client.gui.utils.RuntimeCryptedFile;
import pro.gravit.utils.enfs.EnFS;
import pro.gravit.utils.enfs.dir.FileEntry;
import pro.gravit.utils.enfs.dir.URLFile;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EnFSHelper {
    public static Path initEnFS(LauncherConfig config) throws IOException {
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
        }
    }
}
