package utb.fai;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private final Set<SocketHandler> activeHandlersSet = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, SocketHandler> handlersByName = new ConcurrentHashMap<>();

    void add(SocketHandler handler) {
        activeHandlersSet.add(handler);
    }

    void remove(SocketHandler handler) {
        activeHandlersSet.remove(handler);
        if (handler.name != null)
            handlersByName.remove(handler.name, handler);
    }

    boolean rename(SocketHandler handler, String newName) {
        SocketHandler owner = handlersByName.putIfAbsent(newName, handler);
        if (owner != null && owner != handler)
            return false;
        String oldName = handler.name;
        handler.name = newName;
        if (oldName != null && !oldName.equals(newName))
            handlersByName.remove(oldName, handler);
        return true;
    }

    void sendToRooms(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet)
            if (handler != sender && handler.sharesRoomWith(sender))
                handler.send(message);
    }

    boolean sendPrivate(String recipientName, String message) {
        SocketHandler recipient = handlersByName.get(recipientName);
        if (recipient == null)
            return false;
        recipient.send(message);
        return true;
    }
}
