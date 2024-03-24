package pro.gravit.launcher.gui;

import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.runtime.utils.HWIDProvider;
import pro.gravit.launcher.base.request.secure.GetSecureLevelInfoRequest;
import pro.gravit.launcher.base.request.secure.HardwareReportRequest;
import pro.gravit.launcher.base.request.secure.VerifySecureLevelKeyRequest;
import pro.gravit.utils.helper.*;

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
                application.service.request(
                                   new VerifySecureLevelKeyRequest(JavaRuntimeModule.engine.publicKey.getEncoded(), signature))
                                   .thenAccept((event1) -> {
                                       if (!event1.needHardwareInfo) {
                                           simpleGetHardwareToken();
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

    private void simpleGetHardwareToken() {
        try {
            application.service.request(new HardwareReportRequest()).thenAccept((response) -> {
                LogHelper.info("Advanced security level success completed");
                notifyWaitObject(true);
            }).exceptionally((e) -> {
                application.messageManager.createNotification("Hardware Checker", e.getCause().getMessage());
                notifyWaitObject(false);
                return null;
            });
        } catch (IOException e) {
            application.messageManager.createNotification("Hardware Checker", e.getCause().getMessage());
            notifyWaitObject(false);
        }
    }

    private void doCollectHardwareInfo(boolean needSerial) {
        CommonHelper.newThread("HardwareInfo Collector Thread", true, () -> {
            try {
                HWIDProvider provider = new HWIDProvider();
                HardwareReportRequest.HardwareInfo info = provider.getHardwareInfo(needSerial);
                HardwareReportRequest reportRequest = new HardwareReportRequest();
                reportRequest.hardware = info;
                application.service.request(reportRequest).thenAccept((event) -> {
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
            if (waitObject[0] == null) waitObject.wait(3000);
            return waitObject[0];
        }
    }

    public byte[] sign(byte[] data) {
        return SecurityHelper.sign(data, JavaRuntimeModule.engine.privateKey);
    }
}