package pro.gravit.launcher.gui.scenes.authmethods;

import pro.gravit.launcher.core.api.method.AuthMethodPassword;
import pro.gravit.launcher.gui.basic.Layer;

import java.util.concurrent.CompletableFuture;

public abstract class AuthMethodUI extends Layer {

    public abstract CompletableFuture<LoginAndPassword> auth();

    public abstract void onAuthButtonClicked();

    public record LoginAndPassword(String login, AuthMethodPassword password) {

    }
}
