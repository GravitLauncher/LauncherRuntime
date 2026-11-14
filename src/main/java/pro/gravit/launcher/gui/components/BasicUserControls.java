package pro.gravit.launcher.gui.components;

import javafx.scene.control.ButtonBase;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.launcher.gui.core.impl.FxComponent;
import pro.gravit.launcher.gui.core.impl.ContextHelper;

public class BasicUserControls extends FxComponent {

    public BasicUserControls(Pane layout, JavaFXApplication application) {
        super(layout, application);
    }

    @Override
    public String getName() {
        return "userControls";
    }

    @Override
    protected void doInit() {
        var nodes = new pro.gravit.launcher.gui.core.ui.ViewNodes(layout, "window header");
        bind(nodes, "#exit", this::onExit);
        bind(nodes, "#minimize", this::onMinimize);
        bind(nodes, "#deauth", this::onDeauth);
    }

    private void bind(pro.gravit.launcher.gui.core.ui.ViewNodes nodes, String selector, Runnable operation) {
        nodes.optional(ButtonBase.class, "#controls", selector).ifPresent(button -> {
            var action = new pro.gravit.launcher.gui.core.ui.UiAction(button.getText(), () -> {
                operation.run(); return java.util.concurrent.CompletableFuture.completedFuture(null);
            }, this::errorHandle);
            action.attach(scope, button);
            if (button.getTooltip() != null) button.setAccessibleText(button.getTooltip().getText());
        });
    }

    protected void onExit() {
        currentStage.close();
    }

    protected void onMinimize() {
        currentStage.hide();
    }

    protected void onDeauth() {
        application.messageManager.showApplyDialog(
                application.getTranslation("runtime.scenes.settings.exitDialog.header"),
                application.getTranslation("runtime.scenes.settings.exitDialog.description"),
                this::userExit, () -> {}, true);
    }

    protected void userExit() {
        processRequest(application.getTranslation("runtime.scenes.settings.exitDialog.processing"),
                       LauncherBackendAPIHolder.getApi().userExit(),
                       (event) -> {
                           // Exit to main menu
                           ContextHelper.runInFxThreadStatic(() -> {
                               application.gui.loginScene.reset();
                               try {
                                   application.authService.exit();
                                   switchScene(application.gui.loginScene);
                               } catch (Exception ex) {
                                   errorHandle(ex);
                               }
                           });
                       }, (event) -> {});
    }

    @Override
    public void reset() {

    }
}
