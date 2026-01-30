package cc.rexsystems.rexChat.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when chat is muted or unmuted
 * 
 * @since 1.6.0
 */
public class ChatMuteEvent extends Event {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final boolean muted;
    private final String executor;
    
    public ChatMuteEvent(boolean muted, String executor) {
        this.muted = muted;
        this.executor = executor;
    }
    
    /**
     * Check if chat is being muted
     * 
     * @return true if muted, false if unmuted
     */
    public boolean isMuted() {
        return muted;
    }
    
    /**
     * Get the name of who executed the mute/unmute
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
