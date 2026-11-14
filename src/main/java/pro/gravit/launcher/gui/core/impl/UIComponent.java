package pro.gravit.launcher.gui.core.impl;

import javafx.scene.layout.Pane;

public interface UIComponent {
    Pane getLayout();

    boolean isInit();

    String getName();

    void init() throws Exception;

    void postInit() throws Exception;

    void reset();

    void disable();

    void enable();

    void onShow();

    void onHide();

    default void dispose() { }
}
