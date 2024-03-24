package pro.gravit.launcher.gui.impl;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import pro.gravit.launcher.gui.JavaFXApplication;
import pro.gravit.launcher.gui.config.RuntimeSettings;
import pro.gravit.launcher.gui.helper.LookupHelper;
import pro.gravit.launcher.base.request.RequestException;
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
    private final String sysFxmlPath;
    private Parent sysFxmlRoot;
    private CompletableFuture<Node> sysFxmlFuture;
    boolean isInit;
    boolean isPostInit;
    protected boolean isResetOnShow = false;

    protected AbstractVisualComponent(String fxmlPath, JavaFXApplication application) {
        this.application = application;
        this.sysFxmlPath = fxmlPath;
        this.contextHelper = new ContextHelper(this);
        this.fxExecutor = new FXExecutorService(contextHelper);
        if (application.guiModuleConfig.lazy) {
            this.sysFxmlFuture = application.fxmlFactory.getAsync(sysFxmlPath);
        }
    }

    public static FadeTransition fade(Node region, double delay, double from, double to,
            EventHandler<ActionEvent> onFinished) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), region);
        if (onFinished != null) fadeTransition.setOnFinished(onFinished);
        fadeTransition.setDelay(Duration.millis(delay));
        fadeTransition.setFromValue(from);
        fadeTransition.setToValue(to);
        fadeTransition.play();
        return fadeTransition;
    }

    protected void initBasicControls(Parent header) {
        if (header == null) {
            LogHelper.warning("Scene %s header button(#close, #hide) deprecated", getName());
            LookupHelper.<ButtonBase>lookupIfPossible(layout, "#close")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(layout, "#hide")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
        } else {
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#exit")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.close()));
            LookupHelper.<ButtonBase>lookupIfPossible(header, "#controls", "#minimize")
                        .ifPresent((b) -> b.setOnAction((e) -> currentStage.hide()));
        }
        currentStage.enableMouseDrag(layout);
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
                if (sysFxmlFuture == null) {
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
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
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

    public void postInit() throws Exception {
        if (!isPostInit) {
            doPostInit();
            isPostInit = true;
        }
    }

    protected abstract void doInit();

    protected abstract void doPostInit();

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
            message = "%s: %s".formatted(e.getClass().getName(), e.getMessage());
        } else {
            message = application.getTranslation("runtime.request.".concat(message), message);
        }
        LogHelper.error(e);
        application.messageManager.createNotification("Error", message);
    }

    protected Parent getFxmlRootPrivate() {
        return getFxmlRoot();
    }

    public boolean isDisableReturnBack() {
        return false;
    }
}
