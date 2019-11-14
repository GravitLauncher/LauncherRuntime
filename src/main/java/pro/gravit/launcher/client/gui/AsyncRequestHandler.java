package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.events.ExceptionEvent;
import pro.gravit.launcher.events.RequestEvent;
import pro.gravit.launcher.events.request.ErrorRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.launcher.request.WebSocketEvent;
import pro.gravit.launcher.request.websockets.ClientWebSocketService;
import pro.gravit.launcher.request.websockets.StandartClientWebSocketService;
import pro.gravit.launcher.request.websockets.WebSocketRequest;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncRequestHandler implements ClientWebSocketService.EventHandler {
    private final ClientWebSocketService service;
    private final ConcurrentHashMap<UUID, CompletableFuture> futureMap = new ConcurrentHashMap<>();

    public AsyncRequestHandler(ClientWebSocketService service) {
        this.service = service;
    }

    @Override
    @SuppressWarnings("unchecked cast")
    public void process(WebSocketEvent webSocketEvent) {
        if(webSocketEvent instanceof RequestEvent)
        {
            RequestEvent event = (RequestEvent) webSocketEvent;
            CompletableFuture future = futureMap.get(event.requestUUID);
            if(future != null) {
                if (event instanceof ErrorRequestEvent) {
                    future.completeExceptionally(new RequestException(((ErrorRequestEvent) event).error));
                } else if (event instanceof ExceptionEvent) {
                    future.completeExceptionally(new RequestException(
                            String.format("LaunchServer internal error: %s %s", ((ExceptionEvent) event).clazz, ((ExceptionEvent) event).message)));
                } else
                    future.complete(event);
                futureMap.remove(event.requestUUID);
                LogHelper.dev("Future UUID %s processed", event.requestUUID);
            }
        }
    }
    public<T extends WebSocketEvent> CompletableFuture<T> request(Request<T> request) throws IOException {
        CompletableFuture<T> result = new CompletableFuture<T>();
        futureMap.put(request.requestUUID, result);
        service.sendObject(request, WebSocketRequest.class);
        return result;
    }
}
