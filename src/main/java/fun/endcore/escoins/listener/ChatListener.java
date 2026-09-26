package fun.endcore.escoins.listener;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.cosmetics.CosmeticColor;
import fun.endcore.escoins.cosmetics.CosmeticEntry;
import fun.endcore.escoins.cosmetics.CosmeticType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Optional;

/**
 * Listens for both Paper AsyncChatEvent and Bukkit AsyncPlayerChatEvent.
 * Guarantees that chat colors are applied regardless of whether
 * modern Paper chat or external legacy chat formatters (EssentialsXChat, LPC, etc.) are active.
 */
public class ChatListener implements Listener {
    private final ESCoins plugin;

    public ChatListener(ESCoins plugin) {
        this.plugin = plugin;
    }

    // ========================================================
    // Modern Paper Chat Pipeline (AsyncChatEvent)
    // ========================================================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChatColor(AsyncChatEvent event) {
        applyAsyncChatColor(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChatRenderer(AsyncChatEvent event) {
        if (plugin.getChatManager() == null || !plugin.getChatManager().isEnabled()) {
            return;
        }

        event.renderer((source, sourceDisplayName, message, viewer) ->
                plugin.getChatManager().renderChatMessage(source, message)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChatColorFallback(AsyncChatEvent event) {
        // Fallback check to ensure message color on modern chat if external plugins reset it
        applyAsyncChatColor(event);
    }

    private void applyAsyncChatColor(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCosmeticManager() == null || !plugin.getCosmeticManager().isChatColorEnabled()) {
            return;
        }

        Optional<CosmeticEntry> optColor = plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), CosmeticType.CHAT_COLOR);
        if (optColor.isPresent()) {
            CosmeticColor cc = plugin.getCosmeticManager().getChatColor(optColor.get().color());
            if (cc != null) {
                event.message(event.message().color(cc.namedTextColor()));
                if (plugin.isDebugCosmeticsEnabled()) {
                    plugin.getLogger().info("[DEBUG Cosmetics] Paper AsyncChatEvent: colored message with " + cc.name() + " for " + player.getName());
                }
            }
        }
    }

    // ========================================================
    // Legacy / Bukkit Chat Pipeline (AsyncPlayerChatEvent)
    // Used by EssentialsXChat, LPC, VentureChat, TownyChat, etc.
    // ========================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLegacyChatColorLowest(AsyncPlayerChatEvent event) {
        applyLegacyChatColor(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacyChatHighest(AsyncPlayerChatEvent event) {
        applyLegacyChatColor(event);
    }

    private void applyLegacyChatColor(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCosmeticManager() == null || !plugin.getCosmeticManager().isChatColorEnabled()) {
            return;
        }

        Optional<CosmeticEntry> optColor = plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), CosmeticType.CHAT_COLOR);
        if (optColor.isPresent()) {
            CosmeticColor cc = plugin.getCosmeticManager().getChatColor(optColor.get().color());
            if (cc != null) {
                String colorCode = ChatColor.translateAlternateColorCodes('&', cc.chatCode());
                String message = event.getMessage();
                // Avoid prepending if message already starts with this color code
                if (!message.startsWith(colorCode) && !message.startsWith(cc.chatCode())) {
                    event.setMessage(colorCode + message);
                    if (plugin.isDebugCosmeticsEnabled()) {
                        plugin.getLogger().info("[DEBUG Cosmetics] Legacy AsyncPlayerChatEvent: applied color " + cc.name() + " (" + cc.chatCode() + ") for " + player.getName());
                    }
                }
            }
        }
    }
}
