package pro.gravit.launcher.gui.scenes.debug;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollToEvent;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import pro.gravit.launcher.gui.core.ui.UiScope;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Virtualizes wrapped log rows using measured pixel offsets. */
final class LogViewportSkin extends SkinBase<ListView<ProcessLogOutput.LogLine>> {
    private final UiScope scope = new UiScope();
    private final Pane rows = new Pane();
    private final Rectangle clip = new Rectangle();
    private final ScrollBar scrollbar = new ScrollBar();
    private final DebugScene.LogCell measuringCell = new DebugScene.LogCell();
    private final Map<String, Double> measuredHeights = new HashMap<>();
    private final Map<Integer, DebugScene.LogCell> accessibleRows = new HashMap<>();
    private ObservableList<ProcessLogOutput.LogLine> observedItems;
    private double[] offsets = new double[16];
    private int cachedCharacters;
    private int measuredRows;
    private int invalidFrom = Integer.MAX_VALUE;
    private double measuredWidth = -1;
    private double lineHeight = 1;
    private int scrollTo = -1;
    private int firstVisibleIndex = -1;
    private ProcessLogOutput.LogLine firstVisibleLine;
    private boolean revealRow;
    private boolean following = true;
    private boolean updatingScrollbar;
    private final ListChangeListener<ProcessLogOutput.LogLine> itemsChanged = change -> {
        while (change.next()) invalidFrom = Math.min(invalidFrom, change.getFrom());
        clearAccessibleRows();
        getSkinnable().requestLayout();
    };

    LogViewportSkin(ListView<ProcessLogOutput.LogLine> output) {
        super(output);
        rows.setClip(clip);
        measuringCell.prepareMeasurement(this::invalidateMeasurements);
        getChildren().setAll(rows, scrollbar, measuringCell);
        scrollbar.setOrientation(Orientation.VERTICAL);
        scrollbar.setFocusTraversable(false);
        scrollbar.setMax(0);
        scope.subscribe(scrollbar.valueProperty().subscribe((oldValue, newValue) -> {
            if (!updatingScrollbar) output.requestLayout();
        }));
        InvalidationListener itemsReplaced = observable -> observeItems();
        output.itemsProperty().addListener(itemsReplaced);
        observeItems();
        scope.add(() -> {
            output.itemsProperty().removeListener(itemsReplaced);
            if (observedItems != null) observedItems.removeListener(itemsChanged);
        });
        EventHandler<ScrollToEvent<Integer>> scrollHandler = event -> scrollTo(event.getScrollTarget(), false);
        output.addEventHandler(ScrollToEvent.scrollToTopIndex(), scrollHandler);
        scope.add(() -> output.removeEventHandler(ScrollToEvent.scrollToTopIndex(), scrollHandler));
    }

    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        List<ProcessLogOutput.LogLine> items = getSkinnable().getItems();
        int count = items == null ? 0 : items.size();
        int oldTop = indexAt(scrollbar.getValue());
        double offsetInRow = scrollbar.getValue() - offsets[oldTop];
        boolean changedAboveViewport = invalidFrom <= oldTop && oldTop == firstVisibleIndex;
        double barWidth = snapSizeX(scrollbar.prefWidth(height));
        // Reserve the scrollbar gutter so its visibility cannot reflow the log.
        double rowWidth = Math.max(1, width - barWidth);
        boolean widthChanged = measuredWidth != rowWidth;
        if (widthChanged) resetMeasurements();
        if (invalidFrom == 0) lineHeight = measuringCell.measure("", rowWidth);
        measureRows(items, count, rowWidth);
        measuredWidth = rowWidth;

        double totalHeight = offsets[count];
        double maxOffset = Math.max(0, totalHeight - height);
        double target = scrollbar.getValue();
        if (scrollTo >= 0 && count > 0) {
            int index = Math.min(count - 1, scrollTo);
            if (!revealRow || offsets[index] < target || offsets[index + 1] - offsets[index] > height) {
                target = offsets[index];
            } else if (offsets[index + 1] > target + height) {
                target = offsets[index + 1] - height;
            }
        } else if (following) {
            target = maxOffset;
        } else if (count > 0 && changedAboveViewport) {
            // The bounded log can discard older entries while this row is being read.
            int newTop = oldTop < count && firstVisibleLine.equals(items.get(oldTop)) ? oldTop : items.indexOf(firstVisibleLine);
            target = newTop >= 0 ? rowOffset(newTop, offsetInRow) : 0;
        } else if (widthChanged && oldTop < count) {
            target = rowOffset(oldTop, offsetInRow);
        }
        scrollTo = -1;
        updatingScrollbar = true;
        try {
            scrollbar.setMax(maxOffset);
            scrollbar.setVisible(maxOffset > 0);
            scrollbar.setVisibleAmount(totalHeight > 0 ? maxOffset * height / totalHeight : 0);
            scrollbar.setUnitIncrement(lineHeight);
            scrollbar.setBlockIncrement(height);
            scrollbar.setValue(Math.clamp(target, 0, maxOffset));
        } finally {
            updatingScrollbar = false;
        }
        scrollbar.resizeRelocate(x + rowWidth, y, barWidth, height);

        rows.resizeRelocate(x, y, rowWidth, height);
        clip.setWidth(rowWidth);
        clip.setHeight(height);
        layoutRows(items, count, rowWidth, height);
    }

    private double rowOffset(int index, double offsetInRow) {
        return Math.min(offsets[index] + offsetInRow, offsets[index + 1] - 1);
    }

    private void measureRows(List<ProcessLogOutput.LogLine> items, int count, double width) {
        if (offsets.length <= count) offsets = Arrays.copyOf(offsets, Math.max(count + 1, offsets.length * 2));
        for (int i = Math.min(invalidFrom, measuredRows); i < count; i++) {
            String text = items.get(i).text();
            Double height = measuredHeights.get(text);
            if (height == null) {
                height = measuringCell.measure(text, width);
                measuredHeights.put(text, height);
                cachedCharacters += text.length();
            }
            offsets[i + 1] = offsets[i] + height;
        }
        if (cachedCharacters > 2 * ProcessLogOutput.MAX_LENGTH || measuredHeights.size() > 2 * ProcessLogOutput.MAX_LINES) {
            measuredHeights.clear();
            cachedCharacters = 0;
            for (int i = 0; i < count; i++) {
                String text = items.get(i).text();
                if (measuredHeights.putIfAbsent(text, offsets[i + 1] - offsets[i]) == null) {
                    cachedCharacters += text.length();
                }
            }
        }
        measuredRows = count;
        invalidFrom = Integer.MAX_VALUE;
    }

    private void observeItems() {
        var items = getSkinnable().getItems();
        if (observedItems == items) return;
        if (observedItems != null) observedItems.removeListener(itemsChanged);
        observedItems = items;
        if (items != null) items.addListener(itemsChanged);
        invalidFrom = 0;
        clearAccessibleRows();
        getSkinnable().requestLayout();
    }

    private void layoutRows(List<ProcessLogOutput.LogLine> items, int count, double width, double height) {
        double offset = scrollbar.getValue();
        int first = indexAt(offset);
        firstVisibleIndex = first < count && height > 0 ? first : -1;
        firstVisibleLine = firstVisibleIndex >= 0 ? items.get(firstVisibleIndex) : null;
        int used = 0;
        for (int i = first; i < count && offsets[i] < offset + height; i++) {
            DebugScene.LogCell cell;
            if (used < rows.getChildren().size()) {
                cell = (DebugScene.LogCell) rows.getChildren().get(used);
            } else {
                cell = new DebugScene.LogCell();
                cell.setManaged(false);
                cell.updateListView(getSkinnable());
                rows.getChildren().add(cell);
            }
            used++;
            if (cell.getIndex() != i) {
                cell.updateIndex(i);
                cell.applyCss();
            }
            cell.setVisible(true);
            cell.resizeRelocate(0, offsets[i] - offset, width, offsets[i + 1] - offsets[i]);
        }
        for (int i = used; i < rows.getChildren().size(); i++) {
            DebugScene.LogCell cell = (DebugScene.LogCell) rows.getChildren().get(i);
            if (cell.isVisible()) {
                cell.setVisible(false);
                cell.updateIndex(-1);
            }
        }
    }

    private int indexAt(double offset) {
        int index = Arrays.binarySearch(offsets, 0, measuredRows + 1, offset);
        return Math.max(0, index >= 0 ? index : -index - 2);
    }

    private void invalidateMeasurements() {
        resetMeasurements();
        getSkinnable().requestLayout();
    }

    private void resetMeasurements() {
        measuredHeights.clear();
        cachedCharacters = 0;
        invalidFrom = 0;
    }

    double lineHeight() { return lineHeight; }

    double viewportHeight() { return rows.getHeight(); }

    void scrollBy(double pixels) {
        if (invalidFrom != Integer.MAX_VALUE) getSkinnable().layout();
        scrollbar.setValue(Math.clamp(scrollbar.getValue() + pixels, 0, scrollbar.getMax()));
    }

    boolean atBottom() { return scrollbar.getValue() >= scrollbar.getMax(); }

    boolean isFollowing() { return following; }

    void setFollowing(boolean following) {
        this.following = following;
        if (following) {
            scrollTo = -1;
            getSkinnable().requestLayout();
        }
    }

    void scrollTo(int index, boolean reveal) {
        scrollTo = index;
        revealRow = reveal;
        getSkinnable().requestLayout();
    }

    int indexAtSceneY(double sceneY) {
        double y = Math.clamp(rows.sceneToLocal(0, sceneY).getY(), 0, Math.max(0, viewportHeight() - 1));
        return indexInViewport(y);
    }

    int indexInViewport(double y) {
        return Math.min(measuredRows - 1, indexAt(scrollbar.getValue() + y));
    }

    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch (attribute) {
            case ITEM_COUNT -> observedItems == null ? 0 : observedItems.size();
            case ITEM_AT_INDEX -> accessibleRow((Integer) parameters[0]);
            case FOCUS_ITEM -> accessibleRow(Math.max(0, getSkinnable().getFocusModel().getFocusedIndex()));
            case SELECTED_ITEMS -> {
                var selected = FXCollections.<Node>observableArrayList();
                for (int index : getSkinnable().getSelectionModel().getSelectedIndices()) {
                    var row = accessibleRow(index);
                    if (row != null) selected.add(row);
                }
                yield selected;
            }
            case VERTICAL_SCROLLBAR -> scrollbar;
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    @Override
    protected void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (action == AccessibleAction.SHOW_ITEM && parameters[0] instanceof ListCell<?> cell) {
            setFollowing(false);
            scrollTo(cell.getIndex(), true);
        } else if (action == AccessibleAction.SET_SELECTED_ITEMS && parameters[0] instanceof List<?> selected) {
            var model = getSkinnable().getSelectionModel();
            model.clearSelection();
            for (Object item : selected) {
                if (item instanceof ListCell<?> cell) model.select(cell.getIndex());
            }
        } else {
            super.executeAccessibleAction(action, parameters);
        }
    }

    private DebugScene.LogCell accessibleRow(Integer index) {
        if (index == null || observedItems == null || index < 0 || index >= observedItems.size()) return null;
        int slot = index - firstVisibleIndex;
        if (firstVisibleIndex >= 0 && slot >= 0 && slot < rows.getChildren().size()
                && rows.getChildren().get(slot) instanceof DebugScene.LogCell cell && cell.isVisible()) {
            return cell;
        }
        return accessibleRows.computeIfAbsent(index, row -> {
            var cell = new DebugScene.LogCell();
            cell.setManaged(false);
            cell.setVisible(false);
            cell.updateListView(getSkinnable());
            cell.updateIndex(row);
            getChildren().add(cell);
            return cell;
        });
    }

    private void clearAccessibleRows() {
        if (accessibleRows.isEmpty()) return;
        getChildren().removeAll(accessibleRows.values());
        accessibleRows.clear();
    }

    @Override
    public void dispose() {
        scope.close();
        super.dispose();
    }
}
