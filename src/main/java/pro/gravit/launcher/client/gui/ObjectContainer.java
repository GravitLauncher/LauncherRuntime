package pro.gravit.launcher.client.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ObjectContainer {
    private Map<UUID, Object> objects = new ConcurrentHashMap<>();
    public<T> UUID push(T o)
    {
        UUID uuid = UUID.randomUUID();
        objects.put(uuid, o);
        return uuid;
    }
    public<T> void push(UUID uuid, T o)
    {
        objects.put(uuid, o);
    }
    @SuppressWarnings("unchecked")
    public<T> T get(UUID uuid)
    {
        return (T) objects.get(uuid);
    }
    @SuppressWarnings("unchecked")
    public<T> T get(Class<? extends T> clazz)
    {
        for(Map.Entry<UUID, Object> e : objects.entrySet())
        {
            if(clazz.isAssignableFrom(e.getValue().getClass()))
                return (T) clazz;
        }
        return null;
    }
}
