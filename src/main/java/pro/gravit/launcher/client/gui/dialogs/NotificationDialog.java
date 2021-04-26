package pro.gravit.launcher.client.gui.dialogs;

import javafx.geometry.Rectangle2D;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.helper.PositionHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NotificationDialog extends AbstractDialog {
    private static class NotificationSlotsInfo {
        private final Set<Integer> set = new HashSet<>(4);
        int get() {
            for(int i=0;i<100;++i) {
                if(!set.contains(i)) {
                    return i;
                }
            }
            return 0;
        }
        void add(int slot) {
            set.add(slot);
        }
        void remove(int slot) {
            set.remove(slot);
        }
    }
    private static final Map<PositionHelper.PositionInfo, NotificationSlotsInfo> slots = new HashMap<>();
    private String header;
    private String text;

    private Text textHeader;
    private Text textDescription;
    private PositionHelper.PositionInfo positionInfo;
    private int positionSlot;
    protected NotificationDialog(JavaFXApplication application, String header, String text) {
        super("components/notification.fxml", application);
        this.header = header;
        this.text = text;
    }

    @Override
    protected void doInit() throws Exception {
        currentStage.stage.setAlwaysOnTop(true);
        textHeader = LookupHelper.lookup(layout, "#notificationHeading");
        textDescription = LookupHelper.lookup(layout, "#notificationText");
        textHeader.setText(header);
        textDescription.setText(text);
    }

    @Override
    public void reset() {
        super.reset();
    }

    public void setPosition(PositionHelper.PositionInfo position) {
        if(positionInfo != null) {
            NotificationSlotsInfo slotsInfo = slots.get(positionInfo);
            slotsInfo.remove(positionSlot);
        }
        this.positionInfo = position;
        if(position == null) return;
        NotificationSlotsInfo slotsInfo = slots.putIfAbsent(position, new NotificationSlotsInfo());
        if(slotsInfo == null) throw new NullPointerException();
        positionSlot = slotsInfo.get();
        slotsInfo.add(positionSlot);
    }

    public void setHeader(String header) {
        this.header = header;
        if(isInit())
            textHeader.setText(header);
    }

    public void setText(String text) {
        this.text = text;
        if(isInit())
            textDescription.setText(text);
    }

    @Override
    public LookupHelper.Point2D getOutSceneCoords(Rectangle2D bounds) {
        if(positionInfo == null) return super.getOutSceneCoords(bounds);
        return PositionHelper.calculate(positionInfo, layout.getPrefWidth(), layout.getPrefHeight(), 20*positionSlot, 0, bounds.getMaxX(), bounds.getMaxY());
    }

    @Override
    public LookupHelper.Point2D getSceneCoords(Pane root) {
        if(positionInfo == null) return super.getSceneCoords(root);
        return PositionHelper.calculate(positionInfo, layout.getPrefWidth(), layout.getPrefHeight(), 20*positionSlot, 0, root.getPrefWidth(), root.getPrefHeight());
    }
}
