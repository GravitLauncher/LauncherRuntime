package pro.gravit.launcher.gui.core;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import pro.gravit.launcher.core.api.model.SelfUser;
import pro.gravit.launcher.core.api.model.Texture;
import pro.gravit.launcher.core.backend.LauncherBackendAPIHolder;
import pro.gravit.launcher.gui.core.ui.FxDispatcher;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkinManager {
    private final JavaFXApplication application;
    private final ReadOnlyObjectWrapper<Image> avatar = new ReadOnlyObjectWrapper<>();
    private final Map<String, Image> cache = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> entry) { return size() > 32; }
    };
    private long generation;
    private String username;

    public SkinManager(JavaFXApplication application) { this.application = application; }
    public ReadOnlyObjectProperty<Image> avatarProperty() { return avatar.getReadOnlyProperty(); }
    public void clear() { FxDispatcher.checkThread(); generation++; username = null; avatar.set(null); }
    public void load(SelfUser user) {
        FxDispatcher.checkThread();
        clear();
        if (user == null || user.getAssets() == null) return;
        username = user.getUsername();
        Texture texture = user.getAssets().get("AVATAR");
        boolean skin = texture == null;
        if (skin) texture = user.getAssets().get("SKIN");
        if (texture != null) loadTexture(texture, skin);
    }
    public void loadSkin(String name, Texture texture) {
        FxDispatcher.checkThread();
        if (java.util.Objects.equals(name, username)) loadTexture(texture, true);
    }
    private void loadTexture(Texture texture, boolean skin) {
        long request = ++generation;
        String key = texture.getUrl() + "#" + texture.getHash() + ":" + skin;
        Image cached = cache.get(key);
        if (cached != null) { avatar.set(cached); return; }
        LauncherBackendAPIHolder.getApi().fetchTexture(texture)
                .thenApplyAsync(bytes -> decode(bytes, skin), application.workers)
                .whenComplete((image, error) -> FxDispatcher.INSTANCE.execute(() -> {
                    if (request != generation) return;
                    if (error == null) { cache.put(key, image); avatar.set(image); }
                    else org.slf4j.LoggerFactory.getLogger(SkinManager.class).warn("Avatar could not be loaded", error);
                }));
    }
    static Image decode(byte[] bytes, boolean skin) {
        if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504e470d0a1a0aL)
            throw new IllegalArgumentException("Expected PNG texture");
        int width = ByteBuffer.wrap(bytes, 16, 8).getInt();
        int height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        if (width <= 0 || height <= 0 || width > 1024 || height > 1024)
            throw new IllegalArgumentException("Invalid texture dimensions");
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError()) throw new IllegalArgumentException("Invalid texture", image.getException());
        if (!skin) return image;
        if (width % 64 != 0 || (height != width && height * 2 != width))
            throw new IllegalArgumentException("Invalid skin dimensions");
        int size = width / 8;
        WritableImage head = new WritableImage(size, size);
        var reader = image.getPixelReader();
        var writer = head.getPixelWriter();
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            var base = reader.getColor(size + x, size + y);
            var overlay = reader.getColor(size * 5 + x, size + y);
            double alpha = overlay.getOpacity();
            double outputAlpha = alpha + base.getOpacity() * (1 - alpha);
            if (outputAlpha == 0) writer.setColor(x, y, javafx.scene.paint.Color.TRANSPARENT);
            else writer.setColor(x, y, new javafx.scene.paint.Color(
                    (overlay.getRed()*alpha + base.getRed()*base.getOpacity()*(1-alpha))/outputAlpha,
                    (overlay.getGreen()*alpha + base.getGreen()*base.getOpacity()*(1-alpha))/outputAlpha,
                    (overlay.getBlue()*alpha + base.getBlue()*base.getOpacity()*(1-alpha))/outputAlpha, outputAlpha));
        }
        return head;
    }
    public Image getScaledFxSkinHead(String name, int width, int height) {
        Image source = avatar.get();
        if (source == null || width <= 0 || height <= 0) return source;
        int sourceWidth = (int) source.getWidth();
        int sourceHeight = (int) source.getHeight();
        if (sourceWidth == width && sourceHeight == height) return source;
        WritableImage scaled = new WritableImage(width, height);
        var reader = source.getPixelReader();
        var writer = scaled.getPixelWriter();
        for (int y = 0; y < height; y++) {
            int sourceY = (int) ((long) y * sourceHeight / height);
            for (int x = 0; x < width; x++) {
                int sourceX = (int) ((long) x * sourceWidth / width);
                writer.setArgb(x, y, reader.getArgb(sourceX, sourceY));
            }
        }
        return scaled;
    }
}
