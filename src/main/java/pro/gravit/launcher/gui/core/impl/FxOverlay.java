package pro.gravit.launcher.gui.core.impl;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;
import pro.gravit.launcher.gui.core.utils.JavaFxUtils;

public abstract class FxOverlay extends FxComponent {
    private int users;
    private FadeTransition transition;
    private AutoCloseable inputBlock;
    private Node previousFocus;
    private EventHandler<ActionEvent> shown;
    private EventHandler<ActionEvent> hidden;

    protected FxOverlay(String path, JavaFXApplication application) { super(path, application); }

    public final void hide(double delay, EventHandler<ActionEvent> callback) {
        FxDispatcher.checkThread();
        if (users == 0 || --users > 0) { complete(callback); return; }
        stopTransition();
        completeShown();
        hidden = callback;
        Node root = getFxmlRoot();
        transition = JavaFxUtils.fade(root, delay, root.getOpacity(), 0, event -> {
            transition = null;
            currentStage.pull(root);
            releaseInput();
            onHide();
            if (previousFocus != null && previousFocus.getScene() == currentStage.getScene() && !previousFocus.isDisabled()) previousFocus.requestFocus();
            previousFocus = null;
            completeHidden();
        });
    }

    public void show(FxStage stage, EventHandler<ActionEvent> callback) throws Exception {
        FxDispatcher.checkThread();
        if (users > 0 && currentStage != stage) throw new IllegalStateException("Overlay already belongs to another window");
        currentStage = stage;
        init();
        if (++users > 1) { complete(callback); return; }
        stopTransition();
        completeHidden();
        Node root = getFxmlRoot();
        if (inputBlock == null) {
            previousFocus = stage.getScene().getFocusOwner();
            root.setOpacity(0);
            inputBlock = stage.blockInput();
        }
        if (!stage.contains(root)) stage.push(root);
        root.setFocusTraversable(true);
        root.requestFocus();
        onShow();
        shown = callback;
        transition = JavaFxUtils.fade(root, 0, root.getOpacity(), 1, event -> {
            transition = null;
            completeShown();
        });
    }

    private void stopTransition() {
        if (transition != null) { transition.stop(); transition = null; }
    }
    private void completeShown() { var callback = shown; shown = null; complete(callback); }
    private void completeHidden() { var callback = hidden; hidden = null; complete(callback); }
    private static void complete(EventHandler<ActionEvent> callback) { if (callback != null) callback.handle(new ActionEvent()); }
    private void releaseInput() {
        var block = inputBlock;
        inputBlock = null;
        if (block != null) {
            try { block.close(); }
            catch (Exception error) { throw new IllegalStateException("Unable to release overlay", error); }
        }
    }

    @Override public void dispose() {
        if (disposed) return;
        stopTransition();
        users = 0;
        try {
            if (currentStage != null && layout != null) currentStage.pull(getFxmlRoot());
            releaseInput();
        } finally {
            super.dispose();
            completeShown();
            completeHidden();
        }
    }
}
