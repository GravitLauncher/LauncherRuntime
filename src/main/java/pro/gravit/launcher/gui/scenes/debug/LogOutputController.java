package pro.gravit.launcher.gui.scenes.debug;

import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import pro.gravit.launcher.gui.core.ui.UiScope;

import java.util.function.Function;
import java.util.function.Supplier;

/** Mouse selection and viewport following for the process log. All access is on the FX thread. */
final class LogOutputController {
    private final ListView<ProcessLogOutput.LogLine> output;
    private final UiScope scope = new UiScope();
    private final LogViewportSkin viewport;
    private ObservableList<ProcessLogOutput.LogLine> observedItems;
    private int anchor = -1;
    private final ListChangeListener<ProcessLogOutput.LogLine> contentListener = change -> {
        while (change.next()) {
            if (anchor >= change.getFrom()) {
                if (anchor < change.getFrom() + change.getRemovedSize()) anchor = -1;
                else anchor += change.getAddedSize() - change.getRemovedSize();
            }
        }
    };
    private boolean scrollbarPressed;
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
            if (delta != 0) {
                viewport.scrollBy(delta);
                extendSelection(pointerY);
            }
        }
    };

    LogOutputController(ListView<ProcessLogOutput.LogLine> output, LogViewportSkin viewport) {
        this.output = output;
        this.viewport = viewport;
        scope.add(() -> {
            if (output.getSkin() == viewport) output.setSkin(null);
        });
        output.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        InvalidationListener itemsReplaced = observable -> observeItems();
        output.itemsProperty().addListener(itemsReplaced);
        observeItems();
        scope.add(() -> {
            output.itemsProperty().removeListener(itemsReplaced);
            if (observedItems != null) observedItems.removeListener(contentListener);
        });
        scope.subscribe(output.sceneProperty().subscribe((oldScene, newScene) -> {
            if (newScene == null) {
                stopDragging();
                scrollbarPressed = false;
            }
        }));
        scope.add(this::stopDragging);
        addFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
        addFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!dragging) return;
            pointerY = event.getSceneY();
            extendSelection(pointerY);
            event.consume();
        });
        addFilter(MouseEvent.MOUSE_RELEASED, this::released);
        addFilter(ScrollEvent.SCROLL, this::scrolled);
        addFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
    }

    private void observeItems() {
        var items = output.getItems();
        if (observedItems == items) return;
        if (observedItems != null) observedItems.removeListener(contentListener);
        observedItems = items;
        if (items != null) items.addListener(contentListener);
        followLatest();
    }

    private <E extends Event> void addFilter(EventType<E> type, EventHandler<E> handler) {
        output.addEventFilter(type, handler);
        scope.add(() -> output.removeEventFilter(type, handler));
    }

    private void scrolled(ScrollEvent event) {
        double pixels = switch (event.getTextDeltaYUnits()) {
            case NONE -> event.getDeltaY();
            case LINES -> event.getTextDeltaY() * viewport.lineHeight();
            case PAGES -> event.getTextDeltaY() * viewport.viewportHeight();
        };
        if (pixels == 0) return;
        if (pixels > 0) pauseFollowing();
        viewport.scrollBy(-pixels);
        if (pixels < 0 && !scrollbarPressed && !dragging) resumeAtBottom();
        event.consume();
    }

    private void keyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (event.isShortcutDown() && code == KeyCode.C) {
            if (!output.getSelectionModel().isEmpty()) copyText(DebugScene.selectedText(output));
        } else if (code == KeyCode.ESCAPE || (code == KeyCode.END && !event.isShiftDown())) {
            followLatest();
        } else if (event.isShortcutDown() && code == KeyCode.A) {
            pauseFollowing();
            output.getSelectionModel().selectAll();
        } else if (!navigateByKey(event)) {
            return;
        }
        event.consume();
    }

    private boolean navigateByKey(KeyEvent event) {
        int count = output.getItems().size();
        if (count == 0) return false;
        int focused = output.getFocusModel().getFocusedIndex();
        KeyCode code = event.getCode();
        boolean page = code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN;
        if (page) {
            pauseFollowing();
            viewport.scrollBy(code == KeyCode.PAGE_DOWN ? viewport.viewportHeight() : -viewport.viewportHeight());
        }
        int index = switch (code) {
            case HOME -> 0;
            case END -> count - 1;
            case UP -> Math.max(0, focused - 1);
            case DOWN -> Math.min(count - 1, focused + 1);
            case PAGE_UP -> viewport.indexInViewport(0);
            case PAGE_DOWN -> viewport.indexInViewport(Math.max(0, viewport.viewportHeight() - 1));
            case SPACE -> focused;
            default -> -1;
        };
        if (index < 0 || index >= count) return false;
        pauseFollowing();
        if (event.isShiftDown()) {
            if (anchor < 0) anchor = Math.max(0, focused);
            selectRange(index);
        } else {
            if (event.isShortcutDown() && code != KeyCode.SPACE) {
                output.getFocusModel().focus(index);
            } else {
                output.getSelectionModel().clearAndSelect(index);
                anchor = index;
            }
        }
        if (!page) viewport.scrollTo(index, true);
        if (!event.isShiftDown() && (code == KeyCode.DOWN || code == KeyCode.PAGE_DOWN)) {
            if (!page) output.layout();
            resumeAtBottom();
        }
        return true;
    }

    private void pressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        if (isScrollBar(event.getTarget())) {
            scrollbarPressed = true;
            pauseFollowing();
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
        pauseFollowing();
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

    private void released(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        if (dragging) {
            stopDragging();
            if (output.getSelectionModel().isEmpty()) resumeAtBottom();
            event.consume();
        } else if (scrollbarPressed) {
            scrollbarPressed = false;
            resumeAtBottom();
        }
    }

    private void stopDragging() {
        dragging = false;
        dragScroll.stop();
    }

    private void selectRange(int index) {
        if (anchor < 0) return;
        output.getSelectionModel().clearSelection();
        output.getSelectionModel().selectRange(Math.min(anchor, index), Math.max(anchor, index) + 1);
        output.getFocusModel().focus(index);
    }

    private void extendSelection(double sceneY) {
        int index = viewport.indexAtSceneY(sceneY);
        if (index >= 0) selectRange(index);
    }

    private boolean isScrollBar(Object target) {
        Node node = target instanceof Node value ? value : null;
        while (node != null && node != output) {
            if (node instanceof ScrollBar) return true;
            node = node.getParent();
        }
        return false;
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
        scope.add(() -> output.setContextMenu(null));
        addFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            Node node = event.getTarget() instanceof Node target ? target : null;
            while (node != null && !(node instanceof ListCell<?>)) node = node.getParent();
            if (node instanceof ListCell<?> cell && !cell.isEmpty()
                    && !output.getSelectionModel().isSelected(cell.getIndex())) {
                pauseFollowing();
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

    private void pauseFollowing() {
        viewport.setFollowing(false);
    }

    private void followLatest() {
        stopDragging();
        startFollowing();
    }

    private void startFollowing() {
        viewport.setFollowing(true);
        output.getSelectionModel().clearSelection();
        anchor = -1;
    }

    private void resumeAtBottom() {
        if (viewport.atBottom()) startFollowing();
        else pauseFollowing();
    }

    boolean isFollowing() { return viewport.isFollowing(); }

    void dispose() {
        scope.close();
    }
}
