package pro.gravit.launcher.client.gui.commands.runtime;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.impl.ContextHelper;
import pro.gravit.launcher.client.gui.overlays.AbstractOverlay;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.stage.PrimaryStage;
import pro.gravit.utils.command.Command;

public class WarpCommand extends Command {
    private JavaFXApplication application;

    public WarpCommand(JavaFXApplication application) {
        this.application = application;
    }

    @Override
    public String getArgsDescription() {
        return "[scene/overlay] [name]";
    }

    @Override
    public String getUsageDescription() {
        return "warp to any scene/overlay";
    }

    @Override
    public void invoke(String... args) throws Exception {
        verifyArgs(args, 2);
        if(application == null) application = JavaFXApplication.getInstance();
        if(args[0].equals("scene")) {
            AbstractScene scene = application.gui.getSceneByName(args[1]);
            if(scene == null) {
                throw new IllegalArgumentException(String.format("Scene %s not found", args[1]));
            }
            PrimaryStage stage = application.getMainStage();
            ContextHelper.runInFxThreadStatic(() -> {
                stage.setScene(scene);
                if(!stage.isShowing()) {
                    stage.show();
                }
            });
        }
        else if(args[0].equals("overlay")) {
            AbstractOverlay overlay = application.gui.getOverlayByName(args[1]);
            if(overlay == null) {
                throw new IllegalArgumentException(String.format("Overlay %s not found", args[1]));
            }
            PrimaryStage stage = application.getMainStage();
            if(stage.isNullScene()) {
                throw new IllegalStateException("Please wrap to scene before");
            }
            AbstractScene scene = (AbstractScene) stage.getVisualComponent();
            ContextHelper.runInFxThreadStatic(() -> {
                scene.showOverlay(overlay, e -> {});
            });
        } else {
            throw new IllegalArgumentException(String.format("%s not found", args[0]));
        }
    }
}
