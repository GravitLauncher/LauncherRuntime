package pro.gravit.launcher.client;

import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.request.secure.GetSecureLevelInfoRequest;
import pro.gravit.launcher.request.secure.VerifySecureLevelKeyRequest;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;

public class RuntimeSecurityService {
    private final JavaFXApplication application;
    private final Boolean[] waitObject = new Boolean[]{null};

    public RuntimeSecurityService(JavaFXApplication application) {
        this.application = application;
    }

    public void startRequest() throws IOException
    {
        application.service.request(new GetSecureLevelInfoRequest()).thenAccept((event) -> {
            if(!event.enabled || event.verifySecureKey == null)
            {
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
    private void notifyWaitObject(boolean state)
    {
        synchronized (waitObject)
        {
            waitObject[0] = state;
            waitObject.notifyAll();
        }
    }
    public boolean getSecurityState() throws InterruptedException {
        synchronized (waitObject)
        {
            if(waitObject[0] == null)
                waitObject.wait(3000);
            return waitObject[0];
        }
    }
    public byte[] sign(byte[] data)
    {
        return SecurityHelper.sign(data, JavaRuntimeModule.engine.privateKey);
    }
}
