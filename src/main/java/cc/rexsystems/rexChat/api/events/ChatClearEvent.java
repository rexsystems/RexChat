package cc.rexsystems.rexChat.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when chat is cleared
 * 
 * @since 1.6.0
 */
public class ChatClearEvent extends Event {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final String executor;
    
    public ChatClearEvent(String executor) {
        this.executor = executor;
    }
    
    /**
     * Get the name of who cleared the chat
     * 
     * @return Executor name
     */
    public String getExecutor() {
        return executor;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
