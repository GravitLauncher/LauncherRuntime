package pro.gravit.launcher.client.gui.impl;

import pro.gravit.launcher.client.gui.JavaFXApplication;

public abstract class AbstractDialog extends AbstractVisualComponent {
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
}
