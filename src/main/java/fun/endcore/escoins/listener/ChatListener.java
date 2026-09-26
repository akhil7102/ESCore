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
 * Guarantees that chat colors and player tags are applied regardless of whether
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
        Player player = event.getPlayer();
        if (plugin.getCosmeticManager() == null || !plugin.getCosmeticManager().isChatColorEnabled()) {
            return;
        }

        Optional<CosmeticEntry> optColor = plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), CosmeticType.CHAT_COLOR);
        if (optColor.isPresent()) {
            CosmeticColor cc = plugin.getCosmeticManager().getChatColor(optColor.get().color());
            if (cc != null) {
                // Apply player's active chat color to the outgoing message component
                Component coloredMessage = event.message().color(cc.namedTextColor());
                event.message(coloredMessage);
                if (plugin.isDebugCosmeticsEnabled()) {
                    plugin.getLogger().info("[DEBUG Cosmetics] Paper AsyncChatEvent: colored message with " + cc.name() + " for " + player.getName());
                }
            }
        }
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

    // ========================================================
    // Legacy / Bukkit Chat Pipeline (AsyncPlayerChatEvent)
    // Used by EssentialsXChat, LPC, VentureChat, TownyChat, etc.
    // ========================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLegacyChatColor(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCosmeticManager() == null || !plugin.getCosmeticManager().isChatColorEnabled()) {
            return;
        }

        Optional<CosmeticEntry> optColor = plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), CosmeticType.CHAT_COLOR);
        if (optColor.isPresent()) {
            CosmeticColor cc = plugin.getCosmeticManager().getChatColor(optColor.get().color());
            if (cc != null) {
                String colorCode = ChatColor.translateAlternateColorCodes('&', cc.chatCode());
                event.setMessage(colorCode + event.getMessage());
                if (plugin.isDebugCosmeticsEnabled()) {
                    plugin.getLogger().info("[DEBUG Cosmetics] Legacy AsyncPlayerChatEvent: prefixed message with color " + cc.name() + " (" + cc.chatCode() + ") for " + player.getName());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacyChatTag(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getTagManager() == null) {
            return;
        }

        String activeTag = plugin.getTagManager().getActiveTag(player.getUniqueId());
        String activeTagDisplay = plugin.getTagManager().getActiveTagDisplay(player.getUniqueId());
        if (activeTag == null || activeTagDisplay == null || activeTagDisplay.isEmpty()) {
            return;
        }

        String format = event.getFormat();
        // Check if the format already contains the tag ID or display (avoid double insertion)
        String strippedTag = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', activeTagDisplay)).trim();
        if (format.contains(activeTag) || (!strippedTag.isEmpty() && format.contains(strippedTag))) {
            return;
        }

        String coloredTag = ChatColor.translateAlternateColorCodes('&', activeTagDisplay);
        // In standard Bukkit format strings (e.g. "%1$s: %2$s"), %1$s is the player display name.
        // Insert active tag directly following the player name: DEV AKHILPLAYZYT [PHANTOM] > hello
        if (format.contains("%1$s")) {
            event.setFormat(format.replace("%1$s", "%1$s " + coloredTag));
            if (plugin.isDebugCosmeticsEnabled()) {
                plugin.getLogger().info("[DEBUG Cosmetics] Legacy AsyncPlayerChatEvent: injected tag " + activeTag + " into format for " + player.getName());
            }
        }
    }
}
