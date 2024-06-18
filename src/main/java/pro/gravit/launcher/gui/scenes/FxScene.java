package pro.gravit.launcher.gui.scenes;

import pro.gravit.launcher.gui.basic.Layer;
import pro.gravit.launcher.gui.components.ControlButtons;
import pro.gravit.utils.helper.LogHelper;

public abstract class FxScene extends Layer {
    @Override
    protected void doInit() {
        use(lookup("#controls"), ControlButtons::new);
    }

    protected void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    protected<T> T errorHandleFuture(Throwable e) {
        errorHandle(e);
        return null;
    }
}
