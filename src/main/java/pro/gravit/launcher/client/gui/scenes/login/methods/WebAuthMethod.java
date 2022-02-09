package pro.gravit.launcher.client.gui.scenes.login.methods;

import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.login.LoginScene;
import pro.gravit.launcher.request.auth.details.AuthWebViewDetails;
import pro.gravit.launcher.request.auth.password.AuthCodePassword;
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
        this.overlay = application.gui.registerOverlay(WebAuthOverlay.class);
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
    public CompletableFuture<LoginScene.LoginAndPasswordResult> auth(AuthWebViewDetails details) {
        overlay.future = new CompletableFuture<>();
        overlay.follow(details.url, details.redirectUrl, (r) -> {
            String code = r;
            LogHelper.debug("Code: %s", code);
            if(code.startsWith("?code=")) {
                code = r.substring("?code=".length(), r.indexOf("&"));
            }
            LogHelper.debug("Code: %s", code);
            overlay.future.complete(new LoginScene.LoginAndPasswordResult(null, new AuthCodePassword(code)));
        });
        return overlay.future;
    }

    @Override
    public CompletableFuture<Void> hide() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        accessor.hideOverlay(10, (r) -> {
            future.complete(null);
        });
        return future;
    }

    @Override
    public boolean isSavable() {
        return false;
    }

    public static class WebAuthOverlay extends AbstractOverlay {
        private WebView webView;
        private LoginScene.LoginSceneAccessor accessor;
        private CompletableFuture<LoginScene.LoginAndPasswordResult> future;

        public WebAuthOverlay(JavaFXApplication application) {
            super("overlay/webauth/webauth.fxml", application);
        }

        @Override
        public String getName() {
            return "webView";
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
                accessor.hideOverlay(0, null);
            });
        }

        public void follow(String url, String redirectUrl, Consumer<String> redirectCallback) {
            LogHelper.debug("Load url %s", url);
            webView.getEngine().setJavaScriptEnabled(true);
            webView.getEngine().load(url);
            if (redirectCallback != null) {
                webView.getEngine().locationProperty().addListener((obs, oldLocation, newLocation) -> {
                    if (newLocation != null) {
                        LogHelper.debug("Location: %s", newLocation);
                        if (redirectUrl != null) {
                            if (newLocation.startsWith(redirectUrl)) {
                                redirectCallback.accept(newLocation.substring(redirectUrl.length()));
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
