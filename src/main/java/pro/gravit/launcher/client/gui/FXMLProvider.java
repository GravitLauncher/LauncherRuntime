package pro.gravit.launcher.client.gui;

import javafx.fxml.FXMLLoader;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class FXMLProvider {
    private final Function<String, FXMLLoader> loaderFactory;
    private final ExecutorService executorService;
    private final Map<String, Object> fxmlCache = new ConcurrentHashMap<>();

    public FXMLProvider(Function<String, FXMLLoader> loaderFactory, ExecutorService executorService) {
        this.loaderFactory = loaderFactory;
        this.executorService = executorService;
    }

    private static class FutureVirtualObject
    {
        public IOException exception;
    }

    public<T> Future<T> queue(String name, InputStream inputStream)
    {
        fxmlCache.put(name, new FutureVirtualObject());
        return executorService.submit( () -> {
            try {
                long start = System.currentTimeMillis();
                T result = rawLoadFxml(name, inputStream);
                Object obj = fxmlCache.get(name);
                fxmlCache.put(name, result);
                if(obj instanceof FutureVirtualObject)
                {
                    synchronized (obj)
                    {
                        obj.notifyAll();
                    }
                }
                long finish = System.currentTimeMillis();
                if(LogHelper.isDebugEnabled())
                    LogHelper.debug("FXML %s(%s) loaded in %d ms", name, result.getClass().getName(), finish - start);
                return result;
            } catch (Throwable e) {
                Object obj = fxmlCache.get(name);
                if(obj instanceof FutureVirtualObject)
                {
                    synchronized (obj)
                    {
                        if(e instanceof IOException)
                        {
                            ((FutureVirtualObject) obj).exception = (IOException) e;
                        }
                        else
                        {
                            ((FutureVirtualObject) obj).exception = new IOException(e);
                        }
                        obj.notifyAll();
                    }
                }
                return null;
            }
        });
    }
    @SuppressWarnings("unchecked cast")
    public<T> T getFxml(String name) throws InterruptedException, IOException {
        Object obj = fxmlCache.get(name);
        if(obj == null) throw new IllegalStateException(String.format("You must need queue fxml load %s", name));
        if(obj instanceof FutureVirtualObject)
        {
            if(((FutureVirtualObject) obj).exception != null) throw ((FutureVirtualObject) obj).exception;
            synchronized (obj)
            {
                obj.wait();
            }
            obj = fxmlCache.get(name);
            if(obj instanceof FutureVirtualObject)
            {
                if(((FutureVirtualObject) obj).exception != null) throw ((FutureVirtualObject) obj).exception;
            }
        }
        return (T) obj;
    }

    public<T> T rawLoadFxml(String name, InputStream inputStream) throws IOException
    {
        T result = loaderFactory.apply(name).load(inputStream);
        inputStream.close();
        return result;
    }
    public<T> Future<T> queueNoCache(String name, InputStream inputStream)
    {
        fxmlCache.put(name, new FutureVirtualObject());
        return executorService.submit( () -> {
            try {
                long start = System.currentTimeMillis();
                T result = rawLoadFxml(name, inputStream);
                long finish = System.currentTimeMillis();
                if(LogHelper.isDebugEnabled())
                    LogHelper.debug("FXML %s(%s) loaded in %d ms(no cache)", name, result.getClass().getName(), finish - start);
                return result;
            } catch (Throwable e) {
                return null;
            }
        });
    }
}
