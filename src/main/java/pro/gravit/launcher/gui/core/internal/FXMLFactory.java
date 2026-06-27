package pro.gravit.launcher.gui.core.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import pro.gravit.launcher.gui.core.JavaFXApplication;
import pro.gravit.utils.helper.IOHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class FXMLFactory {

    private static final Logger logger =
            LoggerFactory.getLogger(FXMLFactory.class);

    private final ResourceBundle resources;
    private final ExecutorService executorService;

    public FXMLFactory(ResourceBundle resources, ExecutorService executorService) {
        this.resources = resources;
        this.executorService = executorService;
    }

    public <T extends Node> CompletableFuture<T> getAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return get(url);
            } catch (IOException e) {
                throw new FXMLLoadException(e);
            }
        }, executorService);
    }

    public <T extends Node> T get(String url) throws IOException {
        long startTime = System.currentTimeMillis();
        FXMLLoader loader = newLoaderInstance(JavaFXApplication.getResourceURL(url));
        long loaderInstanceTime = System.currentTimeMillis();
        try (InputStream inputStream = IOHelper.newInput(JavaFXApplication.getResourceURL(url))) {
            T result = loader.load(inputStream);
            long endTime = System.currentTimeMillis();
            logger.debug("Fxml load url={} thread={} createMs={} loadMs={} totalMs={}", url,
                    Thread.currentThread().getName(), loaderInstanceTime - startTime,
                    endTime - loaderInstanceTime, endTime - startTime);
            return result;
        }
    }

    public FXMLLoader newLoaderInstance(URL url) {
        FXMLLoader loader;
        try {
            loader = new FXMLLoader(url);
            if (resources != null) {
                loader.setResources(resources);
            }
        } catch (Exception e) {
            logger.error("", e);
            return null;
        }
        loader.setCharset(IOHelper.UNICODE_CHARSET);
        loader.setClassLoader(FXMLFactory.class.getClassLoader());
        return loader;
    }

    public static class FXMLLoadException extends RuntimeException {
        public FXMLLoadException() {
        }

        public FXMLLoadException(String message) {
            super(message);
        }

        public FXMLLoadException(String message, Throwable cause) {
            super(message, cause);
        }

        public FXMLLoadException(Throwable cause) {
            super(cause);
        }
    }
}
