package fun.endcore.escoins.listener;

import fun.endcore.escoins.ESCoins;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for Paper AsyncChatEvent and delegates to ChatManager for custom hover tooltips and formatting.
 */
public class ChatListener implements Listener {
    private final ESCoins plugin;

    public ChatListener(ESCoins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (plugin.getChatManager() == null || !plugin.getChatManager().isEnabled()) {
            return;
        }

        event.renderer((source, sourceDisplayName, message, viewer) ->
                plugin.getChatManager().renderChatMessage(source, message)
        );
    }
}
