package pro.gravit.launcher.client.gui.scenes;

import javafx.event.EventHandler;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.management.PingServerRequest;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ServerMenuScene extends AbstractScene {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    private ImageView avatar;

    private List<ClientProfile> lastProfiles;
    private Image originalAvatarImage;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() throws Exception {
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        scrollPane.setOnScroll(e -> {
            double offset = e.getDeltaY()/scrollPane.getWidth();
            scrollPane.setHvalue(scrollPane.getHvalue()-offset);
        });
        reset();
        isResetOnShow = true;
    }
    static class ServerButtonCache
    {
        public CompletableFuture<Pane> pane;
        public int position;
    }
    public static boolean putAvatarToImageView(JavaFXApplication application, String username, ImageView imageView) {
        int width = (int) imageView.getFitWidth();
        int height = (int) imageView.getFitHeight();
        Image head = application.skinManager.getScaledFxSkinHead(username, width, height);
        if(head == null) return false;
        imageView.setImage(head);
        return true;
    }
    public static CompletableFuture<Pane> getServerButton(JavaFXApplication application, ClientProfile profile) {
        UUID profileUUID = profile.getUUID();
        if(profileUUID == null) {
            profileUUID = UUID.randomUUID();
            LogHelper.warning("Profile %s UUID null", profileUUID);
        }
        String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profileUUID);
        URL customFxml = application.tryResource(customFxmlName);
        CompletableFuture<Pane> future;
        if (customFxml != null) {
            future = application.fxmlFactory.getAsync(customFxmlName);
        } else {
            future = application.fxmlFactory.getAsync(SERVER_BUTTON_FXML);
        }
        future = future.thenApply(pane -> {
            LookupHelper.<Labeled>lookup(pane, "#nameServer").setText(profile.getTitle());
            LookupHelper.<Labeled>lookup(pane, "#genreServer").setText(profile.getVersion().toString());
            LookupHelper.<ImageView>lookupIfPossible(pane, "#serverLogo").ifPresent((a) -> {
                try {
                    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(a.getFitWidth(), a.getFitHeight());
                    clip.setArcWidth(10.0);
                    clip.setArcHeight(10.0);
                    a.setClip(clip);
                } catch (Throwable e) {
                    LogHelper.error(e);
                }
            });
            application.stateService.addServerPingCallback(profile.getDefaultServerProfile().name, (report) -> {
                LookupHelper.<Text>lookup(pane, "#online").setText(String.valueOf(report.playersOnline));
            });
            return pane;
        });
        return future;
    }

    @Override
    public void reset() {
        if(lastProfiles == application.stateService.getProfiles()) return;
        lastProfiles = application.stateService.getProfiles();
        Map<ClientProfile, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
        LookupHelper.<Labeled>lookup(layout, "#nickname").setText(application.stateService.getUsername());
        avatar.setImage(originalAvatarImage);
        int position = 0;
        for (ClientProfile profile : application.stateService.getProfiles()) {
            ServerButtonCache cache = new ServerButtonCache();
            cache.pane = getServerButton(application, profile);
            cache.position = position;
            serverButtonCacheMap.put(profile, cache);
            profile.updateOptionalGraph();
            position++;
        }
        ScrollPane scrollPane = LookupHelper.lookup(layout, "#servers");
        HBox serverList = (HBox) scrollPane.getContent();
        serverList.setSpacing(20);
        serverList.getChildren().clear();
        application.stateService.clearServerPingCallbacks();
        serverButtonCacheMap.forEach((profile, serverButtonCache) -> {
            try {
                Pane pane = serverButtonCache.pane.get();
                EventHandler<? super MouseEvent> handle = (event) -> {
                    if (!event.getButton().equals(MouseButton.PRIMARY))
                        return;
                    changeServer(profile);
                    try {
                        switchScene(application.gui.serverInfoScene);
                        application.gui.serverInfoScene.reset();
                    } catch (Exception e) {
                        errorHandle(e);
                    }
                };
                pane.setOnMouseClicked(handle);
                serverList.getChildren().add(pane);
            } catch (InterruptedException | ExecutionException e) {
                errorHandle(e);
            }
        });
        try {
            Request.service.request(new PingServerRequest()).thenAccept((event) -> {
                if(event.serverMap != null)
                {
                    event.serverMap.forEach((name, value) -> {
                        application.stateService.setServerPingReport(event.serverMap);
                    });
                }
            }).exceptionally((ex) -> {
                errorHandle(ex.getCause());
                return null;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        putAvatarToImageView(application, application.stateService.getUsername(), avatar);
    }

    @Override
    public String getName() {
        return "serverMenu";
    }

    private void changeServer(ClientProfile profile) {
        application.stateService.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
    }
}
