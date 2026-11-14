package pro.gravit.launcher.gui.scenes.login.methods;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.gravit.launcher.core.api.method.details.AuthDeviceFlowDetails;
import pro.gravit.launcher.core.api.method.password.AuthDeviceCodePassword;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.impl.FxOverlay;
import pro.gravit.launcher.gui.core.impl.ContextHelper;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.utils.helper.JVMHelper;

import java.awt.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public class DeviceFlowAuthMethod extends AbstractAuthMethod<AuthDeviceFlowDetails> {

    private static final Logger logger =
            LoggerFactory.getLogger(DeviceFlowAuthMethod.class);

    DeviceFlowAuthOverlay overlay;
    private final JavaFXApplication application;
    private final LoginScene.LoginSceneAccessor accessor;

    public DeviceFlowAuthMethod(LoginScene.LoginSceneAccessor accessor) {
        this.application = accessor.getApplication();
        this.accessor = accessor;
        this.overlay = application.gui.registerComponent(DeviceFlowAuthOverlay::new);
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
    public CompletableFuture<Void> show(AuthDeviceFlowDetails details) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            accessor.showOverlay(overlay, (e) -> { if (!future.isDone()) future.complete(null); });
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthDeviceFlowDetails details) {
        CompletableFuture<AuthFlow.LoginAndPasswordResult> future = new CompletableFuture<>();
        overlay.future = future;
        if (overlay.cancelRequested) {
            future.completeExceptionally(new UserAuthCanceledException());
            return future;
        }
        long generation = overlay.beginAttempt();
        try {
            CompletableFuture<AuthDeviceFlowDetails.AuthDeviceFlowDetailsData> supplied = details.dataSupplier().get();
            if (supplied == null) throw new IllegalStateException("Device authorization did not return a future");
            supplied.whenComplete((data, error) -> {
                if (generation != overlay.generation || overlay.future != future || future.isDone()) return;
                if (error != null) {
                    future.completeExceptionally(unwrap(error));
                    return;
                }
                if (data == null || data.deviceCode() == null || data.userCode() == null) {
                    future.completeExceptionally(new IllegalStateException("Device authorization returned incomplete codes"));
                    return;
                }
                ContextHelper.runInFxThreadStatic(() -> {
                    if (generation != overlay.generation || overlay.future != future || future.isDone()) return;
                    overlay.follow(details.url(), data.deviceCode(), data.userCode(), details.externalBrowserSupport(),
                            code -> future.complete(new AuthFlow.LoginAndPasswordResult(null, new AuthDeviceCodePassword(code))));
                }).exceptionally(fxError -> { future.completeExceptionally(unwrap(fxError)); return null; });
            });
        } catch (Throwable error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof java.util.concurrent.ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
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

    public static class DeviceFlowAuthOverlay extends FxOverlay {
        private WebView webView;
        private Button submitBtn;
        private Label codeLabel;
        private LoginScene.LoginSceneAccessor accessor;
        private CompletableFuture<AuthFlow.LoginAndPasswordResult> future;
        private volatile long generation;
        private volatile boolean cancelRequested;
        private Button copyButton;
        private javafx.scene.layout.StackPane webViewContainer;

        public DeviceFlowAuthOverlay(JavaFXApplication application) {
            super("overlay/webauth/webauth.fxml", application);
        }

        @Override
        public String getName() {
            return "deviceFlowView";
        }

        public void hide(EventHandler<ActionEvent> onFinished) {
            hide(10, event -> {
                stopBrowser();
                if (onFinished != null) onFinished.handle(event);
            });
        }

        @Override
        protected void doInit() {
            submitBtn = LookupHelper.lookup(layout, Button.class, "#submit");
            codeLabel = LookupHelper.lookup(layout, Label.class, "#link");
            copyButton = LookupHelper.lookupIfPossible(layout, Button.class, "#copyUrl").orElse(null);
            webViewContainer = LookupHelper.lookupIfPossible(layout, javafx.scene.layout.StackPane.class, "#webViewContainer").orElse(null);
            LookupHelper.lookupIfPossible(layout, Button.class, "#close").ifPresent((b) -> b.setOnAction((e) -> {
                // Завершаем future с отменой чтобы AuthFlow мог сбросить состояние
                cancelCurrent();
                hide(0, event -> stopBrowser());
            }));

            // submit behavior handled in follow() to support device flow marker
            submitBtn.setOnAction((e) -> {});
        }

        long beginAttempt() { return ++generation; }

        void cancelCurrent() {
            cancelRequested = true;
            generation++;
            if (future != null && !future.isDone()) future.completeExceptionally(new AbstractAuthMethod.UserAuthCanceledException());
        }

        public void follow(String url, String deviceCode, String userCode, boolean externalBrowserSupport, Consumer<String> redirectCallback) {
            codeLabel.setText("Code: " + userCode + "\n" + url);
            if (copyButton != null) copyButton.setOnAction(evt -> copyText(url));
            codeLabel.setOnMouseClicked(evt -> copyText(userCode));
            webView = null;
            if (webViewContainer != null) webViewContainer.getChildren().clear();
            boolean embedded = !externalBrowserSupport;
            if (embedded && webViewContainer != null) {
                webView = new WebView();
                webViewContainer.getChildren().add(webView);
            }
            if (webView != null) {
                webView.setManaged(embedded);
                webView.setVisible(embedded);
                if (embedded) webView.getEngine().load(url);
            }
            try {
                if (externalBrowserSupport && JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ex) {
                logger.debug("Unable to open device authorization URL", ex);
            }
            submitBtn.setOnAction((e) -> {
                if (redirectCallback != null) redirectCallback.accept(deviceCode);
            });
        }

        private void copyText(String value) {
            try {
                ClipboardContent content = new ClipboardContent();
                content.putString(value);
                Clipboard.getSystemClipboard().setContent(content);
                codeLabel.setText("Copied to clipboard");
            } catch (Exception ex) { accessor.errorHandle(ex); }
        }

        void stopBrowser() {
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
            stopBrowser();
            cancelRequested = false;
        }
    }
}
