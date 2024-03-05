package pro.gravit.launcher.gui.scenes.login;

import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.request.auth.AuthRequest;

public record SuccessAuth(AuthRequestEvent requestEvent, String recentLogin,
                          AuthRequest.AuthPasswordInterface recentPassword) {
}
