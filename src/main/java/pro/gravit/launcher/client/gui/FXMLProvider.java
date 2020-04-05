package pro.gravit.launcher.client.gui;

import javafx.fxml.FXMLLoader;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

public class FXMLProvider {
    private final Function<String, FXMLLoader> loaderFactory;
    private final ExecutorService executorService;
    private final Map<String, Object> fxmlCache = new ConcurrentHashMap<>();

    public FXMLProvider(Function<String, FXMLLoader> loaderFactory, ExecutorService executorService) {
        this.loaderFactory = loaderFactory;
        this.executorService = executorService;
    }

    public <T> Future<T> queue(String name, InputStream inputStream) {
        LogHelper.dev("FXML queue %s", name);
        fxmlCache.put(name, new FutureVirtualObject());
        return executorService.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                T result = rawLoadFxml(name, inputStream);
                Object cacheEntry = fxmlCache.get(name);
                fxmlCache.put(name, result);
                if (cacheEntry instanceof FutureVirtualObject) {
                    synchronized (cacheEntry) {
                        cacheEntry.notifyAll();
                    }
                }
                long finish = System.currentTimeMillis();
                if (LogHelper.isDebugEnabled())
                    LogHelper.debug("FXML %s(%s) loaded in %d ms", name, result.getClass().getName(), finish - start);
                return result;
            } catch (Throwable e) {
                Object cacheEntry = fxmlCache.get(name);
                if (cacheEntry instanceof FutureVirtualObject) {
                    synchronized (cacheEntry) {
                        if (e instanceof IOException) {
                            ((FutureVirtualObject) cacheEntry).exception = (IOException) e;
                        } else {
                            ((FutureVirtualObject) cacheEntry).exception = new IOException(e);
                        }
                        cacheEntry.notifyAll();
                    }
                }
                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T getFxml(String name) throws InterruptedException, IOException {
        Object cacheEntry = fxmlCache.get(name);
        if (cacheEntry == null)
            throw new IllegalStateException(String.format("You must need queue fxml load %s", name));
        if (cacheEntry instanceof FutureVirtualObject) {
            if (((FutureVirtualObject) cacheEntry).exception != null)
                throw ((FutureVirtualObject) cacheEntry).exception;
            synchronized (cacheEntry) {
                cacheEntry.wait();
            }
            cacheEntry = fxmlCache.get(name);
            if (cacheEntry instanceof FutureVirtualObject) {
                if (((FutureVirtualObject) cacheEntry).exception != null)
                    throw ((FutureVirtualObject) cacheEntry).exception;
            }
        }
        return (T) cacheEntry;
    }

    private <T> T rawLoadFxml(String name, InputStream inputStream) throws IOException {
        T result = loaderFactory.apply(name).load(inputStream);
        inputStream.close();
        return result;
    }

    public <T> Future<T> queueNoCache(String name, InputStream inputStream) {
        fxmlCache.put(name, new FutureVirtualObject());
        return executorService.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                T result = rawLoadFxml(name, inputStream);
                long finish = System.currentTimeMillis();
                if (LogHelper.isDebugEnabled())
                    LogHelper.debug("FXML %s(%s) loaded in %d ms(no cache)", name, result.getClass().getName(), finish - start);
                return result;
            } catch (Throwable e) {
                LogHelper.error(e);
                return null;
            }
        });
    }

    private static class FutureVirtualObject {
        public IOException exception;
    }
}
