package pro.gravit.launcher.client.gui.overlays;

import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;

import java.util.function.Consumer;

public class WebAuthOverlay extends AbstractOverlay {
    private WebView webView;
    public WebAuthOverlay(JavaFXApplication application) {
        super("overlay/webauth/webauth.fxml", application);
    }

    @Override
    protected void doInit() {
        Pane webViewPane = LookupHelper.lookup(layout, "#webview");
        webView = new WebView();
        webViewPane.getChildren().add(webViewPane);
    }

    public void follow(String url, String redirectUrl, Consumer<String> redirectCallback) {
        webView.getEngine().setJavaScriptEnabled(true);
        webView.getEngine().load(url);
        if(redirectCallback != null) {
            webView.getEngine().locationProperty().addListener((obs, oldLocation, newLocation) -> {
                if (newLocation != null) {
                    if(redirectUrl != null) {
                        if(newLocation.endsWith(redirectUrl)) {
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
