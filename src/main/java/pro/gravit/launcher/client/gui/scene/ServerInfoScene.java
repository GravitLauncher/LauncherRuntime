package pro.gravit.launcher.client.gui.scene;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.profiles.ClientProfile;

public class ServerInfoScene extends AbstractScene {
    public ServerInfoScene(JavaFXApplication application) {
        super("scenes/serverinfo/serverinfo.fxml", application);
    }

    @Override
    protected void doInit() throws Exception {
        reset();
    }

    @Override
    public void reset() {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        LookupHelper.<Label>lookupIfPossible(layout, "#serverName").ifPresent((e) -> e.setText(profile.getTitle()));
        LookupHelper.<Label>lookupIfPossible(layout, "#serverDescription").ifPresent((e) -> e.setText(profile.getInfo()));
        LookupHelper.<Pane>lookupIfPossible(layout, "#serverButton").ifPresent((e) -> {
            e.getChildren().clear();
            ServerMenuScene.getServerButton(application, profile).thenAccept(pane -> {
                contextHelper.runInFxThread(() -> {
                    e.getChildren().add(pane);
                });
            });
        });
    }

    @Override
    public void errorHandle(Throwable e) {

    }

    @Override
    public String getName() {
        return null;
    }
}
