package pro.gravit.launcher.gui.scenes.login.methods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.web.WebView;
import javafx.scene.control.Label;

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
    public CompletableFuture<Void> show(AuthWebDetails details) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            accessor.showOverlay(overlay, (e) -> future.complete(null));
        } catch (Exception e) {
            accessor.errorHandle(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthWebDetails details) {
        overlay.future = new CompletableFuture<>();
        overlay.follow(details.url(), details.redirectUrl(), (r) -> {
            logger.info("Redirect uri: {}", r);
            overlay.future.complete(new AuthFlow.LoginAndPasswordResult(null, new AuthOAuthPassword(r)));
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

    public static class WebAuthOverlay extends FxOverlay {
        private WebView webView;
        private Button submitBtn;
        private Label codeLabel;
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
            submitBtn = LookupHelper.lookup(layout, "#submit");
            codeLabel = LookupHelper.lookup(layout, "#link");
            LookupHelper.<Button>lookupIfPossible(layout, "#close").ifPresent((b) -> b.setOnAction((e) -> {
                // Завершаем future с отменой чтобы AuthFlow мог сбросить состояние
                if (future != null && !future.isDone()) {
                    future.completeExceptionally(new AbstractAuthMethod.UserAuthCanceledException());
                }
                hide(0, null);
            }));

            // submit behavior handled in follow() to support device flow marker
            submitBtn.setOnAction((e) -> {});
        }

        public void follow(String url, String redirectUrl, Consumer<String> redirectCallback) {
            logger.info("Open external browser with URL {}", url);
            // detect device flow marker in redirectUrl
            if (redirectUrl != null && redirectUrl.startsWith("device:")) {
                String[] parts = redirectUrl.split(":", 3);
                String deviceCode = parts.length > 1 ? parts[1] : null;
                String userCode = parts.length > 2 ? parts[2] : null;
                if (userCode != null) {
                    codeLabel.setText("Code: " + userCode);
                    codeLabel.setUserData("device:" + (deviceCode != null ? deviceCode : ""));
                    codeLabel.setOnMouseClicked(evt -> {
                        try {
                            Clipboard clipboard = Clipboard.getSystemClipboard();
                            ClipboardContent content = new ClipboardContent();
                            content.putString(userCode);
                            clipboard.setContent(content);
                            codeLabel.setText("Code: " + userCode + " (copied)");
                        } catch (Exception ex) {
                            accessor.errorHandle(ex);
                        }
                    });
                }
            } else {
                codeLabel.setText("");
                codeLabel.setUserData(null);
            }
            // try to open immediately
            try {
                if (JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                accessor.errorHandle(ex);
            }
            // when user clicks submit, prefer pasted URL; if empty and device marker present, use marker
            submitBtn.setOnAction((e) -> {
                String marker = (String) codeLabel.getUserData();
                if (redirectCallback != null) redirectCallback.accept(marker);
            });
        }

        public WebView getWebView() {
            return webView;
        }

        @Override
        public void reset() {

        }
    }
}