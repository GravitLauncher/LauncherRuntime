package pro.gravit.launcher.client;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.events.request.LauncherRequestEvent;
import pro.gravit.launcher.request.secure.GetSecureLevelInfoRequest;
import pro.gravit.launcher.request.secure.VerifySecureLevelKeyRequest;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RuntimeSecurityService {
    private static final Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
    private static final Path C_BINARY_PATH = BINARY_PATH.getParent().resolve(IOHelper.getFileName(BINARY_PATH) + ".tmp");
    private final JavaFXApplication application;
    private final Boolean[] waitObject = new Boolean[]{null};

    public RuntimeSecurityService(JavaFXApplication application) {
        this.application = application;
    }

    public void startRequest() throws IOException {
        application.service.request(new GetSecureLevelInfoRequest()).thenAccept((event) -> {
            if (!event.enabled || event.verifySecureKey == null) {
                LogHelper.info("Advanced security level disabled");
                notifyWaitObject(false);
                return;
            }
            byte[] signature = sign(event.verifySecureKey);
            try {
                application.service.request(new VerifySecureLevelKeyRequest(JavaRuntimeModule.engine.publicKey.getEncoded(), signature))
                        .thenAccept((event1) -> {
                            LogHelper.info("Advanced security level success completed");
                            notifyWaitObject(true);
                        }).exceptionally((e) -> {
                    LogHelper.error(e);
                    notifyWaitObject(false);
                    return null;
                });
            } catch (IOException e) {
                LogHelper.error("VerifySecureLevel failed: %s", e.getMessage());
                notifyWaitObject(false);
            }
        }).exceptionally((e) -> {
            LogHelper.info("Advanced security level disabled(exception)");
            notifyWaitObject(false);
            return null;
        });
    }

    private void notifyWaitObject(boolean state) {
        synchronized (waitObject) {
            waitObject[0] = state;
            waitObject.notifyAll();
        }
    }

    public boolean getSecurityState() throws InterruptedException {
        synchronized (waitObject) {
            if (waitObject[0] == null)
                waitObject.wait(3000);
            return waitObject[0];
        }
    }

    public void update(LauncherRequestEvent result) throws IOException {
        // Rewrite and start new instance
        if (result.binary != null)
            IOHelper.write(BINARY_PATH, result.binary);
        else {
            /*URLConnection connection = IOHelper.newConnection(new URL(result.url));
            connection.setDoOutput(true);
            connection.connect();
            try (OutputStream stream = connection.getOutputStream()) {
                IOHelper.transfer(BINARY_PATH, stream);
            }*/
            try {
                Files.deleteIfExists(C_BINARY_PATH);
                URL url = new URL(result.url);
                URLConnection connection = url.openConnection();
                try (InputStream in = connection.getInputStream()) {
                    IOHelper.transfer(in, C_BINARY_PATH);
                }
                try (InputStream in = IOHelper.newInput(C_BINARY_PATH)) {
                    IOHelper.transfer(in, BINARY_PATH);
                }
                Files.deleteIfExists(C_BINARY_PATH);
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        }
        // Kill current instance
        try {
            Class<?> clazz = Class.forName("java.lang.Shutdown");
            Method halt = clazz.getDeclaredMethod("halt0", int.class);
            halt.setAccessible(true);
            halt.invoke(null, 0);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            LogHelper.error(e);
        }
        throw new AssertionError("Why Launcher wasn't restarted?!");
    }

    public byte[] sign(byte[] data) {
        return SecurityHelper.sign(data, JavaRuntimeModule.engine.privateKey);
    }
}
