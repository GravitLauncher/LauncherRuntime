package pro.gravit.launcher.client.gui.dialogs;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.impl.AbstractVisualComponent;
import pro.gravit.launcher.client.gui.impl.ContextHelper;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDialog extends AbstractVisualComponent {
    private List<ContextHelper.GuiExceptionRunnable> onClose = new ArrayList<>(1);
    protected AbstractDialog(String fxmlPath, JavaFXApplication application) {
        super(fxmlPath, application);
    }

    @Override
    public void reset() {

    }

    @Override
    public void disable() {

    }

    @Override
    public void enable() {

    }

    public void setOnClose(ContextHelper.GuiExceptionRunnable callback) {
        onClose.add(callback);
    }

    public void close() throws Throwable {
        for(ContextHelper.GuiExceptionRunnable callback : onClose) {
            callback.call();
        }
    }

    public LookupHelper.Point2D getOutSceneCoords(Rectangle2D bounds) {

        return new LookupHelper.Point2D(
                (bounds.getMaxX() - layout.getPrefWidth()) / 2.0,
                (bounds.getMaxY() - layout.getPrefHeight()) / 2.0);
    }

    public LookupHelper.Point2D getSceneCoords(Pane root) {

        return new LookupHelper.Point2D(
                (root.getPrefWidth() - layout.getPrefWidth()) / 2.0,
                (root.getPrefHeight() - layout.getPrefHeight()) / 2.0);
    }
}
