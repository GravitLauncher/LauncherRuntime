package pro.gravit.launcher.client.gui.impl;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.utils.FXMLFactory;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.utils.helper.LogHelper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractVisualComponent {
    protected final JavaFXApplication application;
    protected final ContextHelper contextHelper;
    protected final FXExecutorService fxExecutor;
    protected AbstractStage currentStage;
    protected Pane layout;
    private String sysFxmlPath;
    private Parent sysFxmlRoot;
    private CompletableFuture<Node> sysFxmlFuture;
    boolean isInit;
    protected boolean isResetOnShow = false;

    protected AbstractVisualComponent(String fxmlPath, JavaFXApplication application) {
        this.application = application;
        this.sysFxmlPath = fxmlPath;
        this.contextHelper = new ContextHelper(this);
        this.fxExecutor = new FXExecutorService(contextHelper);
        if(application.guiModuleConfig.lazy) {
            this.sysFxmlFuture = application.fxmlFactory.getAsync(sysFxmlPath);
        }
    }

    public static void fade(Node region, double delay, double from, double to, EventHandler<ActionEvent> onFinished) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), region);
        if (onFinished != null)
            fadeTransition.setOnFinished(onFinished);
        fadeTransition.setDelay(Duration.millis(delay));
        fadeTransition.setFromValue(from);
        fadeTransition.setToValue(to);
        fadeTransition.play();
    }

    public Pane getLayout() {
        return layout;
    }

    public boolean isInit() {
        return isInit;
    }

    public abstract String getName();

    protected synchronized Parent getFxmlRoot() {
        try {
            if (sysFxmlRoot == null) {
                if(sysFxmlFuture == null) {
                    this.sysFxmlFuture = application.fxmlFactory.getAsync(sysFxmlPath);
                }
                sysFxmlRoot = (Parent) sysFxmlFuture.get();
            }
            return sysFxmlRoot;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException) {
                cause = cause.getCause();
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new FXMLFactory.FXMLLoadException(cause);
        }
    }

    public void init() throws Exception {
        if (layout == null) {
            layout = (Pane) getFxmlRoot();
        }
        doInit();
        isInit = true;
    }

    protected abstract void doInit() throws Exception;

    public abstract void reset();

    public abstract void disable();

    public abstract void enable();

    public void errorHandle(Throwable e) {
        String message = null;
        if (e instanceof CompletionException) {
            e = e.getCause();
        }
        if (e instanceof ExecutionException) {
            e = e.getCause();
        }
        if (e instanceof RequestException) {
            message = e.getMessage();
        }
        if (message == null) {
            message = String.format("%s: %s", e.getClass().getName(), e.getMessage());
        } else {
            message = application.getTranslation("runtime.request.".concat(message), message);
        }
        LogHelper.error(e);
        application.messageManager.createNotification(message, "Нажмите на уведомление, чтобы скрыть его.");
    }
    public void notificateHandle(String title, String desc) {
        application.messageManager.createNotification(title, desc);
    }
}