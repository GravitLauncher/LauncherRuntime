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
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.gui.scenes.login.AuthFlow;
import pro.gravit.launcher.gui.scenes.login.LoginScene;
import pro.gravit.utils.helper.JVMHelper;

import java.awt.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
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
        this.overlay = application.gui.registerComponent(DeviceFlowAuthOverlay.class);
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
            accessor.showOverlay(overlay, (e) -> future.complete(null));
        } catch (Exception e) {
            accessor.errorHandle(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<AuthFlow.LoginAndPasswordResult> auth(AuthDeviceFlowDetails details) {
        overlay.future = new CompletableFuture<>();
        details.dataSupplier().get().thenAccept((data) -> {
            accessor.runInFxThread(() -> {
                overlay.follow(details.url(), data.deviceCode(), data.userCode(), (r) -> {
                    overlay.future.complete(new AuthFlow.LoginAndPasswordResult(null, new AuthDeviceCodePassword(r)));
                });
            });
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

    public static class DeviceFlowAuthOverlay extends FxOverlay {
        private WebView webView;
        private Button submitBtn;
        private Label codeLabel;
        private LoginScene.LoginSceneAccessor accessor;
        private CompletableFuture<AuthFlow.LoginAndPasswordResult> future;

        public DeviceFlowAuthOverlay(JavaFXApplication application) {
            super("overlay/webauth/webauth.fxml", application);
        }

        @Override
        public String getName() {
            return "deviceFlowView";
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
                    future.completeExceptionally(new UserAuthCanceledException());
                }
                hide(0, null);
            }));

            // submit behavior handled in follow() to support device flow marker
            submitBtn.setOnAction((e) -> {});
        }

        public void follow(String url, String deviceCode, String userCode, Consumer<String> redirectCallback) {
            logger.info("Open external browser with URL {}", url);
            codeLabel.setText("Code: " + userCode);
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
            // try to open immediately
            try {
                if (JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                accessor.errorHandle(ex);
            }
            // when user clicks submit, prefer pasted URL; if empty and device marker present, use marker
            submitBtn.setOnAction((e) -> {
                if (redirectCallback != null) redirectCallback.accept(deviceCode);
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