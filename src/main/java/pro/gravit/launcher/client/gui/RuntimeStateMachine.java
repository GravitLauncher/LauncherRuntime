package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.events.request.AuthRequestEvent;

public class RuntimeStateMachine {
    private AuthRequestEvent rawAuthResult;

    public RuntimeStateMachine setAuthResult(AuthRequestEvent rawAuthResult) {
        this.rawAuthResult = rawAuthResult;
        return this;
    }
}
