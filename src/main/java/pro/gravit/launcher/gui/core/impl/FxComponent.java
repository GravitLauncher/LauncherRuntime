package pro.gravit.launcher.gui.core.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.base.request.RequestException;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.interfaces.ValueComponent;
import pro.gravit.launcher.gui.core.internal.FXMLFactory;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;
import pro.gravit.launcher.gui.core.ui.UiScope;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

public abstract class FxComponent implements UIComponent {
    protected Pane layout;
    protected boolean isInit;
    private boolean isPostInit;
    protected boolean disposed;

    @Override public Pane getLayout() { return layout; }
    @Override public boolean isInit() { return isInit; }
    @Override public void postInit() throws Exception {
        if (disposed) throw new IllegalStateException("Component is disposed");
        if (!isPostInit) { doPostInit(); isPostInit = true; }
    }
    protected abstract void doInit();
    protected void doPostInit() { }
    @Override public void disable() { }
    @Override public void enable() { }
    public boolean isDisableReturnBack() { return false; }


    private static final Logger logger =
            LoggerFactory.getLogger(FxComponent.class);

    protected final JavaFXApplication application;
    protected final ContextHelper contextHelper;
    protected final java.util.concurrent.Executor fxExecutor = FxDispatcher.INSTANCE;
    protected FxStage currentStage;
    private final String sysFxmlPath;
    private Parent sysFxmlRoot;
    protected boolean isResetOnShow = false;
    private final List<UIComponent> ownedComponents = new ArrayList<>();
    protected final UiScope scope = new UiScope();
    protected UiScope visibleScope;

    protected FxComponent(String fxmlPath, JavaFXApplication application) {
        this.application = application;
        this.sysFxmlPath = fxmlPath;
        this.contextHelper = new ContextHelper(this);
    }

    protected FxComponent(Pane layout, JavaFXApplication application) {
        this.application = application;
        this.sysFxmlPath = null;
        this.contextHelper = new ContextHelper(this);
        this.layout = layout;
    }

    protected<T> Void errorHandle(T value, Throwable e) {
        errorHandle(e);
        return null;
    }

    protected synchronized Parent getFxmlRoot() {
        FxDispatcher.checkThread();
        if (sysFxmlRoot == null) {
            if (sysFxmlPath == null) {
                if (layout != null) return layout;
                throw new IllegalStateException("Component has no FXML resource");
            }
            try {
                sysFxmlRoot = (Parent) application.fxmlFactory.get(sysFxmlPath);
            } catch (java.io.IOException e) {
                throw new FXMLFactory.FXMLLoadException(e);
            }
        }
        return sysFxmlRoot;
    }

    protected<T extends UIComponent> T use(Pane layout, BiFunction<Pane, JavaFXApplication, T> constructor) {
        T object = constructor.apply(layout, application);
        try {
            if(object instanceof FxComponent fxComponent) {
                fxComponent.currentStage = currentStage;
            }
            object.init();
            synchronized (ownedComponents) { ownedComponents.add(object); }
            return object;
        } catch (Throwable e) {
            if (object != null) object.dispose();
            throw new IllegalStateException("Unable to initialize child component", e);
        }
    }

    protected<T extends ValueComponent<V>, V> T use(Pane layout, BiFunction<Pane, JavaFXApplication, T> constructor, V value) {
        T object = use(layout, constructor);
        if(object != null) {
            object.onValue(value);
        }
        return object;
    }

    @Override
    public void init() throws Exception {
        if (disposed) throw new IllegalStateException("Cannot initialize a disposed component");
        if (isInit) return;
        if (layout == null) {
            layout = (Pane) getFxmlRoot();
        }
        try {
            doInit();
            isInit = true;
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException cleanupError) { failure.addSuppressed(cleanupError); }
            throw failure;
        }
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        UiScope cleanup = new UiScope();
        cleanup.add(() -> { layout = null; sysFxmlRoot = null; isInit = false; });
        for (UIComponent child : List.copyOf(ownedComponents)) cleanup.add(child::dispose);
        ownedComponents.clear();
        cleanup.add(scope::close);
        cleanup.add(this::onHide);
        cleanup.close();
    }

    @Override
    public void onShow() {
        if (visibleScope == null) visibleScope = new UiScope();
    }

    @Override
    public void onHide() {
        if (visibleScope != null) {
            visibleScope.close();
            visibleScope = null;
        }
    }

    protected void switchScene(FxScene scene) throws Exception {
        currentStage.setScene(scene, true);
    }

    protected void switchToBackScene() throws Exception {
        currentStage.back();
    }

    public void errorHandle(Throwable e) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            Throwable error = e;
            javafx.application.Platform.runLater(() -> errorHandle(error));
            return;
        }
        if (e == null) return;
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
        logger.error("", e);
        application.messageManager.createNotification("Error", message);
    }

    public void errorHandle(String e) {
        logger.error("{}", e);
        application.messageManager.createNotification("Error", e);
    }

    protected Parent getFxmlRootPrivate() {
        return getFxmlRoot();
    }

    public void showOverlay(FxOverlay overlay, EventHandler<ActionEvent> onFinished) throws Exception {
        overlay.show(currentStage, onFinished);
    }

    protected final <T> void processRequest(String message, CompletableFuture<T> request,
            Consumer<T> onSuccess, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(currentStage, message, request, onSuccess, onError);
    }

    protected final <T> void processRequest(String message, CompletableFuture<T> request,
            Consumer<T> onSuccess, Consumer<Throwable> onException, EventHandler<ActionEvent> onError) {
        application.gui.processingOverlay.processRequest(currentStage, message, request, onSuccess, onException, onError);
    }
}
