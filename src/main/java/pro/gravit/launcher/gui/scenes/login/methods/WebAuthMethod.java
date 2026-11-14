package pro.gravit.launcher.gui.scenes.login.methods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.beans.value.ChangeListener;

import java.awt.Desktop;
import java.net.URI;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import pro.gravit.launcher.core.api.method.details.AuthWebDetails;
import pro.gravit.launcher.core.api.method.password.AuthOAuthPassword;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.core.impl.FxOverlay;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.utils.helper.JVMHelper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class WebAuthMethod extends AbstractAuthMethod<AuthWebDetails> {

    private static final Logger logger =
            LoggerFactory.getLogger(WebAuthMethod.class);

    WebAuthOverlay overlay;
    private final JavaFXApplication application;
    private final LoginScene.LoginSceneAccessor accessor;

    public WebAuthMethod(LoginScene.LoginSceneAccessor accessor) {
        this.application = accessor.getApplication();
        this.accessor = accessor;
        this.overlay = application.gui.registerComponent(WebAuthOverlay::new);
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
    public CompletableFuture<Void> show(AuthWebDetails details) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            accessor.showOverlay(overlay, (e) -> {
                if (!future.isDone()) future.complete(null);
            });
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthWebDetails details) {
        overlay.future = new CompletableFuture<>();
        if (overlay.cancelRequested) {
            overlay.future.completeExceptionally(new AbstractAuthMethod.UserAuthCanceledException());
            return overlay.future;
        }
        try {
            overlay.follow(details.url(), details.redirectUrl(), details.externalBrowserSupport(), (r) -> {
                if (overlay.future != null && !overlay.future.isDone())
                    overlay.future.complete(new AuthFlow.LoginAndPasswordResult(null, new AuthOAuthPassword(r)));
            });
        } catch (Throwable error) { overlay.future.completeExceptionally(error); }
        return overlay.future;
    }

    @Override
    public void onAuthClicked() {
    }

    @Override
    public void onUserCancel() {
        overlay.cancelCurrent();
    }

    @Override
    public CompletableFuture<Void> hide() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try { overlay.hide((r) -> { overlay.stopBrowser(); future.complete(null); }); }
        catch (Throwable error) { future.completeExceptionally(error); }
        return future;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    public static class WebAuthOverlay extends FxOverlay {
        private WebView webView;
        private Button submitBtn;
        private Label codeLabel;
        private TextField redirectInput;
        private Button copyButton;
        private StackPane webViewContainer;
        private LoginScene.LoginSceneAccessor accessor;
        private CompletableFuture<AuthFlow.LoginAndPasswordResult> future;
        private volatile boolean cancelRequested;
        private volatile long generation;
        private ChangeListener<String> locationListener;

        public WebAuthOverlay(JavaFXApplication application) {
            super("overlay/webauth/webauth.fxml", application);
        }

        @Override
        public String getName() {
            return "webView";
        }

        public void hide(EventHandler<ActionEvent> onFinished) {
            hide(10, event -> {
                clearLocationListener();
                stopBrowser();
                if (onFinished != null) onFinished.handle(event);
            });
        }

        @Override
        protected void doInit() {
            submitBtn = LookupHelper.lookup(layout, Button.class, "#submit");
            codeLabel = LookupHelper.lookup(layout, Label.class, "#link");
            redirectInput = LookupHelper.lookup(layout, TextField.class, "#redirectInput");
            copyButton = LookupHelper.lookupIfPossible(layout, Button.class, "#copyUrl").orElse(null);
            webViewContainer = LookupHelper.lookupIfPossible(layout, StackPane.class, "#webViewContainer").orElse(null);
            LookupHelper.lookupIfPossible(layout, Button.class, "#close").ifPresent((b) -> b.setOnAction((e) -> {
                // Завершаем future с отменой чтобы AuthFlow мог сбросить состояние
                cancelCurrent();
                hide(0, event -> { clearLocationListener(); stopBrowser(); });
            }));

            // submit behavior handled in follow() to support device flow marker
            submitBtn.setOnAction((e) -> {});
        }

        public void follow(String url, String redirectUrl, boolean externalBrowserSupport, Consumer<String> redirectCallback) {
            clearLocationListener();
            long currentGeneration = ++generation;
            if (webViewContainer != null) {
                webViewContainer.getChildren().clear();
                webView = null;
                if (!externalBrowserSupport) {
                    webView = new WebView();
                    webViewContainer.getChildren().add(webView);
                }
            }
            if (copyButton != null) copyButton.setOnAction(evt -> copyText(url));
            // detect device flow marker in redirectUrl
            if (redirectUrl != null && redirectUrl.startsWith("device:")) {
                String[] parts = redirectUrl.split(":", 3);
                String deviceCode = parts.length > 1 ? parts[1] : null;
                String userCode = parts.length > 2 ? parts[2] : null;
                if (userCode != null) {
                    codeLabel.setText("Code: " + userCode);
                    codeLabel.setUserData("device:" + (deviceCode != null ? deviceCode : ""));
                    codeLabel.setOnMouseClicked(evt -> {
                        copyText(userCode);
                    });
                }
            } else {
                codeLabel.setText(url);
                codeLabel.setUserData(null);
            }
            if (webView != null) {
                boolean embedded = !externalBrowserSupport;
                webView.setVisible(embedded);
                webView.setManaged(embedded);
                if (embedded) {
                    locationListener = (observable, oldLocation, newLocation) -> {
                        if (generation == currentGeneration && matchesRedirect(redirectUrl, newLocation)) redirectCallback.accept(newLocation);
                    };
                    webView.getEngine().locationProperty().addListener(locationListener);
                }
            }
            // Windows opens the system browser automatically; on other platforms the user can open the shown URL.
            try {
                if (externalBrowserSupport && JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ex) {
                logger.debug("Unable to open authentication URL", ex);
            }
            if (!externalBrowserSupport && webView != null) webView.getEngine().load(url);
            submitBtn.setOnAction((e) -> {
                String marker = (String) codeLabel.getUserData();
                String pasted = redirectInput.getText() == null ? "" : redirectInput.getText().trim();
                if (marker != null && !marker.isEmpty()) {
                    if (redirectCallback != null) redirectCallback.accept(marker);
                } else if (!pasted.isEmpty() && matchesRedirect(redirectUrl, pasted)) {
                    if (redirectCallback != null) redirectCallback.accept(pasted);
                } else {
                    codeLabel.setText("Enter the completed redirect URL");
                }
            });
        }

        static boolean matchesRedirect(String expectedValue, String actualValue) {
            if (expectedValue == null || actualValue == null || expectedValue.startsWith("device:")) return false;
            try {
                URI expected = URI.create(expectedValue).normalize();
                URI actual = URI.create(actualValue).normalize();
                if (expected.getScheme() == null || actual.getScheme() == null || !expected.getScheme().equalsIgnoreCase(actual.getScheme())) return false;
                if (expected.getRawUserInfo() != null || actual.getRawUserInfo() != null) return false;
                if (expected.getHost() != null && !expected.getHost().equalsIgnoreCase(actual.getHost())) return false;
                if (expected.getHost() == null && actual.getHost() != null) return false;
                if (effectivePort(expected) != effectivePort(actual)) return false;
                String prefix = expected.getPath() == null ? "" : expected.getPath();
                String path = actual.getPath() == null ? "" : actual.getPath();
                return path.equals(prefix) || (path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/"));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private static int effectivePort(URI uri) {
            if (uri.getPort() != -1) return uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : "http".equalsIgnoreCase(uri.getScheme()) ? 80 : -1;
        }

        private void copyText(String value) {
            try {
                ClipboardContent content = new ClipboardContent();
                content.putString(value);
                Clipboard.getSystemClipboard().setContent(content);
                codeLabel.setText(codeLabel.getUserData() == null ? "Authentication URL copied" : "Code copied: " + codeLabel.getUserData());
            } catch (Exception error) { accessor.errorHandle(error); }
        }

        void cancelCurrent() {
            cancelRequested = true;
            generation++;
            if (future != null && !future.isDone()) future.completeExceptionally(new AbstractAuthMethod.UserAuthCanceledException());
            clearLocationListener();
        }

        private void clearLocationListener() {
            if (webView != null && locationListener != null) webView.getEngine().locationProperty().removeListener(locationListener);
            locationListener = null;
        }

        void stopBrowser() {
            clearLocationListener();
            if (webView != null) webView.getEngine().load(null);
            if (webViewContainer != null) webViewContainer.getChildren().clear();
            webView = null;
        }

        public WebView getWebView() {
            return webView;
        }

        @Override
        public void reset() {
            cancelCurrent();
            cancelRequested = false;
            stopBrowser();
            if (redirectInput != null) redirectInput.clear();
        }
    }
}
