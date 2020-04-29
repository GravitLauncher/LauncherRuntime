package pro.gravit.launcher.client;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.events.request.LauncherRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.secure.GetSecureLevelInfoRequest;
import pro.gravit.launcher.request.secure.VerifySecureLevelKeyRequest;
import pro.gravit.launcher.request.websockets.ClientWebSocketService;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RuntimeSecurityService {
    private static final Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
    private static final Path C_BINARY_PATH = BINARY_PATH.resolveSibling(IOHelper.getFileName(BINARY_PATH) + ".tmp");
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
        List<String> args = new ArrayList<>(8);
        args.add(IOHelper.resolveJavaBin(null).toString());
        if (LogHelper.isDebugEnabled())
            args.add(JVMHelper.jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())));
        args.add("-jar");
        args.add(BINARY_PATH.toString());
        ProcessBuilder builder = new ProcessBuilder(args.toArray(new String[0]));
        builder.inheritIO();
        // Rewrite and start new instance
        try {
            LauncherEngine.modulesManager.invokeEvent(new ClientExitPhase(0));
            Request.service.close();
        } catch (Throwable ignored) {
        }
        Files.deleteIfExists(C_BINARY_PATH);
        if (result.binary != null) {
            IOHelper.write(C_BINARY_PATH, result.binary);
        } else {
            URL url;
            try {
                url = new URL(result.url);
            } catch (MalformedURLException e) {
                throw new IOException(e);
            }
            URLConnection connection = url.openConnection();
            try (InputStream in = connection.getInputStream()) {
                IOHelper.transfer(in, C_BINARY_PATH);
            }
        }
        if (Arrays.equals(SecurityHelper.digest(SecurityHelper.DigestAlgorithm.MD5, C_BINARY_PATH),
                SecurityHelper.digest(SecurityHelper.DigestAlgorithm.MD5, BINARY_PATH)))
            throw new IOException("Invalid update (launcher needs update, but link has old launcher), check LaunchServer config...");

        try (InputStream in = IOHelper.newInput(C_BINARY_PATH)) {
            IOHelper.transfer(in, BINARY_PATH);
        }
        Files.deleteIfExists(C_BINARY_PATH);
        builder.start();
    }

    public byte[] sign(byte[] data) {
        return SecurityHelper.sign(data, JavaRuntimeModule.engine.privateKey);
    }
}