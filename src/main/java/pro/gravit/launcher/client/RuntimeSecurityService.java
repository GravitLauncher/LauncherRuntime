package pro.gravit.launcher.client;

import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.events.request.LauncherRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.secure.GetSecureLevelInfoRequest;
import pro.gravit.launcher.request.secure.HardwareReportRequest;
import pro.gravit.launcher.request.secure.VerifySecureLevelKeyRequest;
import pro.gravit.launcher.request.websockets.ClientWebSocketService;
import pro.gravit.launcher.utils.HWIDProvider;
import pro.gravit.utils.helper.*;

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
                            Request.addExtendedToken("publicKey", event1.extendedToken);
                            if(!event1.needHardwareInfo)
                            {
                                LogHelper.info("Advanced security level success completed");
                                notifyWaitObject(true);
                            }
                            else
                            {
                                doCollectHardwareInfo(!event1.onlyStatisticInfo);
                            }
                        }).exceptionally((e) -> {
                    application.messageManager.createNotification("Hardware Checker", e.getCause().getMessage());
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

    private void doCollectHardwareInfo(boolean needSerial)
    {
        CommonHelper.newThread("HardwareInfo Collector Thread", true, () -> {
            HWIDProvider provider = new HWIDProvider();
            HardwareReportRequest.HardwareInfo info = provider.getHardwareInfo(needSerial);
            HardwareReportRequest reportRequest = new HardwareReportRequest();
            reportRequest.hardware = info;
            try {
                application.service.request(reportRequest).thenAccept((event) -> {
                    Request.addExtendedToken("hardware", event.extendedToken);
                    LogHelper.info("Advanced security level success completed");
                    notifyWaitObject(true);
                }).exceptionally((exc) -> {
                    application.messageManager.createNotification("Hardware Checker", exc.getCause().getMessage());
                    return null;
                });
            } catch (IOException e) {
                LogHelper.error(e);
            }
        }).start();
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
        //To StdJavaRuntimeProvider
        //try (InputStream in = IOHelper.newInput(C_BINARY_PATH)) {
        //    IOHelper.transfer(in, BINARY_PATH);
        //}
        //Files.deleteIfExists(C_BINARY_PATH);
        //builder.start();
        StdJavaRuntimeProvider.launcherUpdateTempPath = C_BINARY_PATH;
        StdJavaRuntimeProvider.processBuilder = builder;
    }

    public byte[] sign(byte[] data) {
        return SecurityHelper.sign(data, JavaRuntimeModule.engine.privateKey);
    }

    public boolean isMayBeDownloadJava()
    {
        return JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE;
    }
}