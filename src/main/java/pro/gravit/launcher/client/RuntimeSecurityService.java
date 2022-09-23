package pro.gravit.launcher.client;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.secure.GetSecureLevelInfoRequest;
import pro.gravit.launcher.request.secure.HardwareReportRequest;
import pro.gravit.launcher.request.secure.VerifySecureLevelKeyRequest;
import pro.gravit.launcher.utils.HWIDProvider;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;

public class RuntimeSecurityService {
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
                            if (event1.hardwareExtendedToken != null) {
                                Request.addExtendedToken("hardware", event1.hardwareExtendedToken);
                            }
                            if (!event1.needHardwareInfo) {
                                LogHelper.info("Advanced security level success completed");
                                notifyWaitObject(true);
                            } else {
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

    private void doCollectHardwareInfo(boolean needSerial) {
        CommonHelper.newThread("HardwareInfo Collector Thread", true, () -> {
            try {
                HWIDProvider provider = new HWIDProvider();
                HardwareReportRequest.HardwareInfo info = provider.getHardwareInfo(needSerial);
                HardwareReportRequest reportRequest = new HardwareReportRequest();
                reportRequest.hardware = info;
                application.service.request(reportRequest).thenAccept((event) -> {
                    Request.addExtendedToken("hardware", event.extendedToken);
                    LogHelper.info("Advanced security level success completed");
                    notifyWaitObject(true);
                }).exceptionally((exc) -> {
                    application.messageManager.createNotification("Hardware Checker", exc.getCause().getMessage());
                    return null;
                });
            } catch (Throwable e) {
                LogHelper.error(e);
                notifyWaitObject(false);
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

    public byte[] sign(byte[] data) {
        return SecurityHelper.sign(data, JavaRuntimeModule.engine.privateKey);
    }
}