package pro.gravit.launcher.client.gui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SkinManager {
    private static class SkinEntry {
        final URL url;
        final String imageUrl;
        SoftReference<Optional<BufferedImage>> imageRef = new SoftReference<>(null);
        SoftReference<Optional<BufferedImage>> avatarRef = new SoftReference<>(null);
        SoftReference<Optional<Image>> fxImageRef = new SoftReference<>(null);
        SoftReference<Optional<Image>> fxAvatarRef = new SoftReference<>(null);

        private SkinEntry(URL url) {
            this.url = url;
            imageUrl = null;
        }

        synchronized BufferedImage getFullImage() {
            Optional<BufferedImage> result = imageRef.get();
            if(result == null) { // It is normal
                result = Optional.ofNullable(downloadSkin(url));
                imageRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }

        synchronized Image getFullFxImage() {
            Optional<Image> result = fxImageRef.get();
            if(result == null) { // It is normal
                BufferedImage image = getFullImage();
                result = Optional.ofNullable(convertToFxImage(image));
                fxImageRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }

        synchronized BufferedImage getHeadImage() {
            Optional<BufferedImage> result = avatarRef.get();
            if(result == null) { // It is normal
                BufferedImage image = getFullImage();
                result = Optional.ofNullable(getHeadFromSkinImage(image));
                avatarRef = new SoftReference<>(result);
            }
            return result.orElse(null);
        }

        synchronized Image getHeadFxImage() {
            Optional<Image> result = fxAvatarRef.get();
            if(result == null) { // It is normal
                BufferedImage image = getHeadImage();
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

    public void addSkin(String username, URL url) {
        map.put(username, new SkinEntry(url));
    }

    public BufferedImage getSkin(String username) {
        SkinEntry entry = map.get(username);
        if(entry == null) return null;
        return entry.getFullImage();
    }

    public BufferedImage getSkinHead(String username) {
        SkinEntry entry = map.get(username);
        if(entry == null) return null;
        return entry.getHeadImage();
    }

    public Image getFxSkin(String username) {
        SkinEntry entry = map.get(username);
        if(entry == null) return null;
        return entry.getFullFxImage();
    }

    public Image getFxSkinHead(String username) {
        SkinEntry entry = map.get(username);
        if(entry == null) return null;
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

    public Image getScaledFxSkinHead(String username, int width, int height) {
        BufferedImage image = getSkinHead(username);
        return convertToFxImage(scaleImage(image, width, height));
    }

    private static BufferedImage scaleImage(BufferedImage origImage, int width, int height) {
        if(origImage == null) return null;
        java.awt.Image resized = origImage.getScaledInstance(width, height, java.awt.Image.SCALE_FAST);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics2D = image.createGraphics();
        graphics2D.drawImage(resized, 0, 0, null);
        graphics2D.dispose();
        return image;
    }

    private static BufferedImage downloadSkin(URL url) {
        try {
            return ImageIO.read(url);
        } catch (IOException e) {
            if(!(e instanceof FileNotFoundException)) LogHelper.error(e);
            return null;
        }
    }

    private static BufferedImage getHeadFromSkinImage(BufferedImage image) {
        int width = image.getWidth();
        int renderScale = width / 64;
        int offset = 8 * renderScale;
        LogHelper.debug("ShinHead debug: W: %d Scale: %d Offset: %d", width, renderScale, offset);
        return image.getSubimage(offset, offset, offset, offset);
    }

    private static Image convertToFxImage(BufferedImage image) {
        if(image == null) return null;
        try {
            return SwingFXUtils.toFXImage(image, null);
        } catch (Throwable e) {
            if(LogHelper.isDebugEnabled()) {
                LogHelper.error(e);
            }
            return convertToFxImageJava8(image);
        }
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
        int scan = image.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel
                ? ((SinglePixelPackedSampleModel) image.getRaster().getSampleModel()).getScanlineStride() : 0;
        PixelFormat<IntBuffer> pf = image.isAlphaPremultiplied() ?
                PixelFormat.getIntArgbPreInstance() :
                PixelFormat.getIntArgbInstance();
        writableImage.getPixelWriter().setPixels(0, 0, bw, bh, pf, raster.getData(), raster.getOffset(), scan);
        return writableImage;
    }
}
