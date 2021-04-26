package pro.gravit.launcher.client.gui.helper;

import javafx.scene.layout.Pane;

public class PositionHelper {
    public enum PositionInfo {
        TOP_LEFT(0,0,true,true),
        TOP_RIGHT(Double.MAX_VALUE, 0, false, true),
        BOTTOM_LEFT(0, Double.MAX_VALUE, false, true),
        BOTTOM_RIGHT(Double.MAX_VALUE, Double.MAX_VALUE, false, false);
        public final double startX;
        public final double startY;
        public final boolean offsetXPlus;
        public final boolean offsetYPlus;

        PositionInfo(double startX, double startY, boolean offsetXPlus, boolean offsetYPlus) {
            this.startX = startX;
            this.startY = startY;
            this.offsetXPlus = offsetXPlus;
            this.offsetYPlus = offsetYPlus;
        }
    }
    private PositionHelper() {
        throw new UnsupportedOperationException();
    }

    public static LookupHelper.Point2D calculate(PositionInfo info, double width, double height, double offsetX, double offsetY, double maxX, double maxY) {
        double x = info.startX;
        double y = info.startY;
        x = Math.min(x, maxX);
        y = Math.min(y, maxY);
        if(info.offsetXPlus) {
            x += width;
            x += offsetX;
        } else {
            x -= width;
            x -= offsetX;
        }
        if(info.offsetYPlus) {
            y += height;
            y += offsetY;
        } else {
            y -= height;
            y -= offsetY;
        }
        return new LookupHelper.Point2D(x,y);
    }
}
