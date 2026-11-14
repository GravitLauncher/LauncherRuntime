package pro.gravit.launcher.gui.scenes.debug;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import java.util.function.Function;
import java.util.function.Supplier;

/** Mouse selection and viewport following for the process log. All access is on the FX thread. */
final class LogOutputController {
    private final ListView<ProcessLogOutput.LogLine> output;
    private final Runnable followAfterLayout = this::followAfterLayout;
    private int anchor = -1;
    private final ListChangeListener<ProcessLogOutput.LogLine> contentListener = change -> {
        while (change.next()) {
            if (anchor >= change.getFrom()) {
                if (anchor < change.getFrom() + change.getRemovedSize()) anchor = -1;
                else anchor += change.getAddedSize() - change.getRemovedSize();
            }
        }
        queueFollow();
    };
    private boolean following = true;
    private boolean followQueued;
    private boolean dragging;
    private double pointerY;
    private final AnimationTimer dragScroll = new AnimationTimer() {
        private long lastScroll;

        @Override
        public void handle(long now) {
            if (now - lastScroll < 50_000_000) return;
            lastScroll = now;
            Bounds bounds = output.localToScene(output.getBoundsInLocal());
            double delta = pointerY < bounds.getMinY() ? -20 : pointerY > bounds.getMaxY() ? 20 : 0;
            VirtualFlow<?> flow = flow();
            if (delta != 0 && flow != null) {
                flow.scrollPixels(delta);
                extendSelection(pointerY);
            }
        }
    };

    LogOutputController(ListView<ProcessLogOutput.LogLine> output) {
        this.output = output;
        output.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        output.getItems().addListener(contentListener);
        output.itemsProperty().addListener((observable, oldItems, newItems) -> {
            if (oldItems != null) oldItems.removeListener(contentListener);
            if (newItems != null) newItems.addListener(contentListener);
            following = true;
            anchor = -1;
            queueFollow();
        });
        output.skinProperty().addListener((observable, oldSkin, newSkin) -> queueFollow());
        output.widthProperty().addListener((observable, oldWidth, newWidth) -> queueFollow());
        output.heightProperty().addListener((observable, oldHeight, newHeight) -> queueFollow());
        output.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removePostLayoutPulseListener(followAfterLayout);
            if (newScene != null) newScene.addPostLayoutPulseListener(followAfterLayout);
            if (newScene == null) {
                dragging = false;
                dragScroll.stop();
            } else queueFollow();
        });
        if (output.getScene() != null) output.getScene().addPostLayoutPulseListener(followAfterLayout);
        output.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
        output.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!dragging) return;
            pointerY = event.getSceneY();
            extendSelection(pointerY);
            event.consume();
        });
        output.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (dragging) {
                dragging = false;
                dragScroll.stop();
                if (output.getSelectionModel().isEmpty()) resumeAtBottom();
                event.consume();
            } else if (isScrollBar(event.getTarget())) {
                Platform.runLater(this::resumeAtBottom);
            }
        });
        output.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() > 0) following = false;
            else if (event.getDeltaY() < 0) Platform.runLater(this::resumeAtBottom);
        });
        output.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
                if (!output.getSelectionModel().isEmpty()) {
                    copyText(DebugScene.selectedText(output));
                }
                event.consume();
            } else if (event.getCode() == KeyCode.END && !event.isShiftDown()) {
                followLatest();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                followLatest();
                event.consume();
            } else if (event.getCode() == KeyCode.HOME || event.getCode() == KeyCode.PAGE_UP
                    || event.getCode() == KeyCode.UP || (event.getCode() == KeyCode.END && event.isShiftDown())
                    || (event.isShortcutDown() && event.getCode() == KeyCode.A)) {
                following = false;
            } else if (event.getCode() == KeyCode.PAGE_DOWN || event.getCode() == KeyCode.DOWN) {
                Platform.runLater(this::resumeAtBottom);
            }
        });
        queueFollow();
    }

    private void pressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        if (isScrollBar(event.getTarget())) {
            following = false;
            return;
        }
        Node node = event.getTarget() instanceof Node target ? target : null;
        while (node != null && !(node instanceof ListCell<?>)) node = node.getParent();
        if (!(node instanceof ListCell<?> cell) || cell.isEmpty()) {
            output.getSelectionModel().clearSelection();
            anchor = -1;
            resumeAtBottom();
            event.consume();
            return;
        }
        output.requestFocus();
        following = false;
        int index = cell.getIndex();
        if (event.isShiftDown() && anchor >= 0) {
            selectRange(index);
        } else {
            anchor = index;
            if (event.isShortcutDown()) {
                if (output.getSelectionModel().isSelected(index)) output.getSelectionModel().clearSelection(index);
                else output.getSelectionModel().select(index);
            } else output.getSelectionModel().clearAndSelect(index);
        }
        output.getFocusModel().focus(index);
        dragging = true;
        pointerY = event.getSceneY();
        dragScroll.start();
        event.consume();
    }

    private void selectRange(int index) {
        if (anchor < 0) return;
        output.getSelectionModel().clearSelection();
        output.getSelectionModel().selectRange(Math.min(anchor, index), Math.max(anchor, index) + 1);
        output.getFocusModel().focus(index);
    }

    private void extendSelection(double sceneY) {
        ListCell<?> nearest = null;
        double distance = Double.MAX_VALUE;
        for (Node node : output.lookupAll(".log-line")) {
            ListCell<?> cell = (ListCell<?>) node;
            if (cell.isEmpty()) continue;
            Bounds bounds = cell.localToScene(cell.getBoundsInLocal());
            double candidate = Math.max(bounds.getMinY() - sceneY, Math.max(0, sceneY - bounds.getMaxY()));
            if (candidate < distance) {
                distance = candidate;
                nearest = cell;
            }
        }
        if (nearest != null) selectRange(nearest.getIndex());
    }

    private boolean isScrollBar(Object target) {
        Node node = target instanceof Node value ? value : null;
        while (node != null && node != output) {
            if (node instanceof ScrollBar) return true;
            node = node.getParent();
        }
        return false;
    }

    private VirtualFlow<?> flow() {
        return output.lookup(".virtual-flow") instanceof VirtualFlow<?> value ? value : null;
    }

    void installContextMenu(Function<String, String> translation, Supplier<String> allText) {
        MenuItem copy = new MenuItem(translation.apply("runtime.scenes.debug.menu.copy"));
        MenuItem copyAll = new MenuItem(translation.apply("runtime.scenes.debug.menu.copyAll"));
        MenuItem clear = new MenuItem(translation.apply("runtime.scenes.debug.menu.clearSelection"));
        MenuItem follow = new MenuItem(translation.apply("runtime.scenes.debug.menu.followLatest"));
        copy.setOnAction(event -> copyText(DebugScene.selectedText(output)));
        copyAll.setOnAction(event -> copyText(allText.get()));
        clear.setOnAction(event -> {
            output.getSelectionModel().clearSelection();
            anchor = -1;
            resumeAtBottom();
        });
        follow.setOnAction(event -> followLatest());
        ContextMenu menu = new ContextMenu(copy, copyAll, new SeparatorMenuItem(), clear, follow);
        menu.setOnShowing(event -> {
            copy.setDisable(output.getSelectionModel().isEmpty());
            copyAll.setDisable(output.getItems().isEmpty());
            clear.setDisable(output.getSelectionModel().isEmpty());
        });
        output.setContextMenu(menu);
        output.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            Node node = event.getTarget() instanceof Node target ? target : null;
            while (node != null && !(node instanceof ListCell<?>)) node = node.getParent();
            if (node instanceof ListCell<?> cell && !cell.isEmpty()
                    && !output.getSelectionModel().isSelected(cell.getIndex())) {
                following = false;
                anchor = cell.getIndex();
                output.getSelectionModel().clearAndSelect(anchor);
            }
        });
    }

    private void copyText(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    void followLatest() {
        dragging = false;
        dragScroll.stop();
        output.getSelectionModel().clearSelection();
        anchor = -1;
        following = true;
        queueFollow();
    }

    void resumeAtBottom() {
        VirtualFlow<?> flow = flow();
        following = false;
        if (flow != null) {
            var last = flow.getLastVisibleCell();
            boolean endVisible = last != null && last.getIndex() == output.getItems().size() - 1
                    && flow.sceneToLocal(last.localToScene(last.getBoundsInLocal())).getMaxY()
                    <= flow.getViewportLength() + 2;
            following = flow.getPosition() >= 0.999999 || endVisible;
        }
        if (following) {
            output.getSelectionModel().clearSelection();
            anchor = -1;
            queueFollow();
        }
    }

    boolean isFollowing() { return following; }

    void dispose() {
        following = false;
        dragging = false;
        dragScroll.stop();
        output.getItems().removeListener(contentListener);
        if (output.getScene() != null) output.getScene().removePostLayoutPulseListener(followAfterLayout);
    }

    private void followAfterLayout() {
        if (!following || dragging || !output.isVisible() || output.getItems().isEmpty()) return;
        VirtualFlow<?> flow = flow();
        if (flow == null || flow.getViewportLength() <= 0) return;
        var last = flow.getLastVisibleCell();
        if (last != null && last.getIndex() == output.getItems().size() - 1
                && flow.sceneToLocal(last.localToScene(last.getBoundsInLocal())).getMaxY()
                <= flow.getViewportLength() + 1) return;
        // Startup CSS and layout can reset a scroll performed by runLater before the first pulse.
        // Reconcile the tail after layout, including when a hidden view first becomes visible.
        output.scrollTo(output.getItems().size() - 1);
        output.layout();
        flow.setPosition(1);
        flow.layout();
    }

    void queueFollow() {
        if (!following || followQueued) return;
        followQueued = true;
        Platform.runLater(() -> {
            followQueued = false;
            if (following && !dragging && !output.getItems().isEmpty() && output.getScene() != null) {
                output.applyCss();
                output.scrollTo(output.getItems().size() - 1);
                output.layout();
                VirtualFlow<?> flow = flow();
                if (flow != null) {
                    // A final row can be taller than the viewport: align its bottom, not its top.
                    flow.setPosition(1);
                    flow.layout();
                }
            }
        });
    }
}
