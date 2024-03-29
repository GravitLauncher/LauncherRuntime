package pro.gravit.launcher.gui.scenes.login.methods;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.launcher.base.request.auth.details.AuthWebViewDetails;
import pro.gravit.launcher.base.request.auth.password.AuthCodePassword;
import pro.gravit.utils.helper.LogHelper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class WebAuthMethod extends AbstractAuthMethod<AuthWebViewDetails> {
    WebAuthOverlay overlay;
    private final JavaFXApplication application;
    private final LoginScene.LoginSceneAccessor accessor;

    public WebAuthMethod(LoginScene.LoginSceneAccessor accessor) {
        this.application = accessor.getApplication();
        this.accessor = accessor;
        this.overlay = application.gui.registerComponent(WebAuthOverlay.class);
        this.overlay.accessor = accessor;
    }

    @Override
    public void prepare() {

    }

    @Override
    public void reset() {
        overlay.reset();
    }

    @Override
    public CompletableFuture<Void> show(AuthWebViewDetails details) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            accessor.showOverlay(overlay, (e) -> future.complete(null));
        } catch (Exception e) {
            accessor.errorHandle(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthWebViewDetails details) {
        overlay.future = new CompletableFuture<>();
        overlay.follow(details.url, details.redirectUrl, (r) -> {
            LogHelper.dev("Redirect uri: %s", r);
            overlay.future.complete(new AuthFlow.LoginAndPasswordResult(null, new AuthCodePassword(r)));
        });
        return overlay.future;
    }

    @Override
    public void onAuthClicked() {
    }

    @Override
    public void onUserCancel() {

    }

    @Override
    public CompletableFuture<Void> hide() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        overlay.hide((r) -> future.complete(null));
        return future;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    public static class WebAuthOverlay extends AbstractOverlay {
        private WebView webView;
        private LoginScene.LoginSceneAccessor accessor;
        private CompletableFuture<AuthFlow.LoginAndPasswordResult> future;

        public WebAuthOverlay(JavaFXApplication application) {
            super("overlay/webauth/webauth.fxml", application);
        }

        @Override
        public String getName() {
            return "webView";
        }

        public void hide(EventHandler<ActionEvent> onFinished) {
            hide(10, onFinished);
        }

        @Override
        protected void doInit() {
            ScrollPane webViewPane = LookupHelper.lookup(layout, "#webview");
            webView = new WebView();
            webViewPane.setContent(new VBox(webView));
            LookupHelper.<Button>lookup(layout, "#exit").setOnAction((e) -> {
                if (future != null) {
                    future.completeExceptionally(new UserAuthCanceledException());
                }
                hide(null);
            });
        }

        public void follow(String url, String redirectUrl, Consumer<String> redirectCallback) {
            LogHelper.dev("Load url %s", url);
            webView.getEngine().setJavaScriptEnabled(true);
            webView.getEngine().load(url);
            if (redirectCallback != null) {
                webView.getEngine().locationProperty().addListener((obs, oldLocation, newLocation) -> {
                    if (newLocation != null) {
                        LogHelper.dev("Location: %s", newLocation);
                        if (redirectUrl != null) {
                            if (newLocation.startsWith(redirectUrl)) {
                                redirectCallback.accept(newLocation);
                            }
                        } else {
                            redirectCallback.accept(newLocation);
                        }
                    }
                });
            }
        }

        public WebView getWebView() {
            return webView;
        }

        @Override
        public void reset() {

        }
    }
}
