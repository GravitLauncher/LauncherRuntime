package pro.gravit.launcher.client.gui.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javafx.fxml.FXMLLoader;

import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

public class FXMLFactory {
    private final ResourceBundle resources;
    private final ExecutorService executorService;

    public FXMLFactory(ResourceBundle resources, ExecutorService executorService) {
        this.resources = resources;
        this.executorService = executorService;
    }

    public <T> CompletableFuture<T> getAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return get(url);
            } catch (IOException e) {
                throw new FXMLLoadException(e);
            }
        }, executorService);
    }

    public <T> T get(String url) throws IOException {
        long startTime = System.currentTimeMillis();
        FXMLLoader loader = newLoaderInstance(JavaFXApplication.getResourceURL(url));
        long loaderInstanceTime = System.currentTimeMillis();
        try (InputStream inputStream = IOHelper.newInput(JavaFXApplication.getResourceURL(url))) {
            T result = loader.load(inputStream);
            long endTime = System.currentTimeMillis();
            LogHelper.debug("Fxml load %s time: c: %d | l: %d | total: %d", url, loaderInstanceTime - startTime,
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
            LogHelper.error(e);
            return null;
        }
        loader.setCharset(IOHelper.UNICODE_CHARSET);
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
