package pro.gravit.launcher.gui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import pro.gravit.launcher.base.Downloader;
import pro.gravit.utils.helper.LogHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.IntBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SkinManager {
    private static final HttpClient client = Downloader.newHttpClientBuilder().build();
    private static class SkinEntry {
        final URI url;
        final URI avatarUrl;
        SoftReference<Optional<BufferedImage>> imageRef = new SoftReference<>(null);
        SoftReference<Optional<BufferedImage>> avatarRef = new SoftReference<>(null);
        SoftReference<Optional<Image>> fxImageRef = new SoftReference<>(null);
        SoftReference<Optional<Image>> fxAvatarRef = new SoftReference<>(null);

        private SkinEntry(URI url) {
            this.url = url;
            this.avatarUrl = null;
        }

        public SkinEntry(URI url, URI avatarUrl) {
            this.url = url;
            this.avatarUrl = avatarUrl;
        }

        synchronized BufferedImage getFullImage() {
            Optional<BufferedImage> result = imageRef.get();
            if (result == null) { // It is normal
                result = Optional.ofNullable(downloadSkin(url));
                imageRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }

        synchronized Image getFullFxImage() {
            Optional<Image> result = fxImageRef.get();
            if (result == null) { // It is normal
                BufferedImage image = getFullImage();
                if (image == null) return null;
                result = Optional.ofNullable(convertToFxImage(image));
                fxImageRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }

        synchronized BufferedImage getHeadImage() {
            Optional<BufferedImage> result = avatarRef.get();
            if (result == null) { // It is normal
                if(avatarUrl != null) {
                    result = Optional.ofNullable(downloadSkin(avatarUrl));
                } else {
                    BufferedImage image = getFullImage();
                    if (image == null) return null;
                    result = Optional.of(sumBufferedImage(getHeadFromSkinImage(image), getHeadLayerFromSkinImage(image)));
                }
                avatarRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }

        synchronized Image getHeadFxImage() {
            Optional<Image> result = fxAvatarRef.get();
            if (result == null) { // It is normal
                BufferedImage image = getHeadImage();
                if (image == null) return null;
                result = Optional.ofNullable(convertToFxImage(image));
                fxAvatarRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }
    }

    private final JavaFXApplication application;
    private final Map<String, SkinEntry> map = new ConcurrentHashMap<>();

    public SkinManager(JavaFXApplication application) {
        this.application = application;
    }

    public void addSkin(String username, URI url) {
        map.put(username, new SkinEntry(url));
    }

    public void addOrReplaceSkin(String username, URI url) {
        SkinEntry entry = map.get(username);
        if(entry == null) {
            map.put(username, new SkinEntry(url));
        } else {
            map.put(username, new SkinEntry(url, entry.avatarUrl));
        }
    }

    public void addSkinWithAvatar(String username, URI url, URI avatarUrl) {
        map.put(username, new SkinEntry(url, avatarUrl));
    }

    public BufferedImage getSkin(String username) {
        SkinEntry entry = map.get(username);
        if (entry == null) return null;
        return entry.getFullImage();
    }

    public BufferedImage getSkinHead(String username) {
        SkinEntry entry = map.get(username);
        if (entry == null) return null;
        return entry.getHeadImage();
    }

    public Image getFxSkin(String username) {
        SkinEntry entry = map.get(username);
        if (entry == null) return null;
        return entry.getFullFxImage();
    }

    public Image getFxSkinHead(String username) {
        SkinEntry entry = map.get(username);
        if (entry == null) return null;
        return entry.getHeadFxImage();
    }

    public BufferedImage getScaledSkin(String username, int width, int height) {
        BufferedImage image = getSkin(username);
        return scaleImage(image, width, height);
    }

    public BufferedImage getScaledSkinHead(String username, int width, int height) {
        BufferedImage image = getSkinHead(username);
        return scaleImage(image, width, height);
    }

    public Image getScaledFxSkin(String username, int width, int height) {
        BufferedImage image = getSkin(username);
        return convertToFxImage(scaleImage(image, width, height));
    }

    public static BufferedImage sumBufferedImage(BufferedImage img1, BufferedImage img2) {
        int wid = Math.max(img1.getWidth(), img2.getWidth());
        int height = Math.max(img1.getHeight(), img2.getHeight());
        BufferedImage result = new BufferedImage(wid, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        Color oldColor = g2.getColor();
        g2.setPaint(Color.WHITE);
        g2.fillRect(0, 0, wid, height);
        g2.setColor(oldColor);
        g2.drawImage(img1, null, 0, 0);
        g2.drawImage(img2, null, 0, 0);
        g2.dispose();
        return result;
    }

    public Image getScaledFxSkinHead(String username, int width, int height) {
        BufferedImage image = getSkinHead(username);
        if (image == null) return null;
        return convertToFxImage(scaleImage(image, width, height));
    }

    private static BufferedImage scaleImage(BufferedImage origImage, int width, int height) {
        if (origImage == null) return null;
        java.awt.Image resized = origImage.getScaledInstance(width, height, java.awt.Image.SCALE_FAST);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics2D = image.createGraphics();
        graphics2D.drawImage(resized, 0, 0, null);
        graphics2D.dispose();
        return image;
    }

    private static BufferedImage downloadSkin(URI url) {
        if(url == null) {
            return null;
        }
        try {
            var response = client.send(HttpRequest.newBuilder()
                                   .uri(url)
                                   .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.85 Safari/537.36")
                                   .timeout(Duration.of(10, ChronoUnit.SECONDS))
                                   .build(), HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode() >= 300 || response.statusCode() < 200) {
                LogHelper.error("Skin %s not found (error %d)", url.toString(), response.statusCode());
                return null;
            }
            try (InputStream input = response.body()) {
                return ImageIO.read(input);
            }
        } catch (IOException | InterruptedException e) {
            LogHelper.error(e);
            return null;
        }
    }

    private static BufferedImage getHeadLayerFromSkinImage(BufferedImage image) {
        int width = image.getWidth();
        int renderScale = width / 64;
        int size = 8 * renderScale;
        int x_offset = 5 * 8 * renderScale;
        int y_offset = 8 * renderScale;
        LogHelper.debug("ShinHead debug: W: %d Scale: %d Offset: %d", width, renderScale, size);
        return image.getSubimage(x_offset, y_offset, size, size);
    }

    private static BufferedImage getHeadFromSkinImage(BufferedImage image) {
        int width = image.getWidth();
        int renderScale = width / 64;
        int offset = 8 * renderScale;
        LogHelper.debug("ShinHead debug: W: %d Scale: %d Offset: %d", width, renderScale, offset);
        return image.getSubimage(offset, offset, offset, offset);
    }

    private static Image convertToFxImage(BufferedImage image) {
        if (image == null) return null;
        return convertToFxImageJava8(image);
    }

    private static Image convertToFxImageJava8(BufferedImage image) {
        int bw = image.getWidth();
        int bh = image.getHeight();
        switch (image.getType()) {
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
                break;
            default:
                BufferedImage converted = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D graphics2D = converted.createGraphics();
                graphics2D.drawImage(image, 0, 0, null);
                graphics2D.dispose();
                image = converted;
                break;
        }
        WritableImage writableImage = new WritableImage(bw, bh);
        DataBufferInt raster = (DataBufferInt) image.getRaster().getDataBuffer();
        int scan = image.getRaster()
                        .getSampleModel() instanceof SinglePixelPackedSampleModel singlePixelPackedSampleModel
                ? singlePixelPackedSampleModel.getScanlineStride()
                : 0;
        PixelFormat<IntBuffer> pf = image.isAlphaPremultiplied()
                ? PixelFormat.getIntArgbPreInstance()
                : PixelFormat.getIntArgbInstance();
        writableImage.getPixelWriter().setPixels(0, 0, bw, bh, pf, raster.getData(), raster.getOffset(), scan);
        return writableImage;
    }
}
