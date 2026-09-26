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
    public void onAsyncChatTagAndColorFallback(AsyncChatEvent event) {
        // Ensure message color on modern chat
        applyAsyncChatColor(event);

        // If ESCore chat manager is enabled, it already formats the tag in renderChatMessage
        if (plugin.getChatManager() != null && plugin.getChatManager().isEnabled()) {
            return;
        }

        if (plugin.getTagManager() == null) {
            return;
        }

        Player player = event.getPlayer();
        String activeTagDisplay = plugin.getTagManager().getActiveTagDisplay(player.getUniqueId());
        if (activeTagDisplay == null || activeTagDisplay.trim().isEmpty()) {
            return;
        }

        // An external modern chat plugin is rendering. Wrap the renderer so the tag is appended to the display name
        io.papermc.paper.chat.ChatRenderer currentRenderer = event.renderer();
        Component tagComponent = Component.space().append(plugin.getMessageManager().parse(activeTagDisplay));
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component newDisplayName = sourceDisplayName.append(tagComponent);
            return currentRenderer.render(source, newDisplayName, message, viewer);
        });
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
        formatLegacyChat(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacyChatMonitor(AsyncPlayerChatEvent event) {
        formatLegacyChat(event);
    }

    private void formatLegacyChat(AsyncPlayerChatEvent event) {
        applyLegacyChatColor(event);
        applyLegacyChatTag(event);
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

    private void applyLegacyChatTag(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getTagManager() == null) {
            return;
        }

        String activeTag = plugin.getTagManager().getActiveTag(player.getUniqueId());
        String activeTagDisplay = plugin.getTagManager().getActiveTagDisplay(player.getUniqueId());
        if (activeTag == null || activeTagDisplay == null || activeTagDisplay.trim().isEmpty()) {
            return;
        }

        String format = event.getFormat();
        String coloredTag = ChatColor.translateAlternateColorCodes('&', activeTagDisplay);
        String strippedTag = ChatColor.stripColor(coloredTag).trim();

        // Guard against duplicate tag injection
        if (format.contains(coloredTag)
                || format.contains(coloredTag.replace("%", "%%"))
                || format.contains(activeTagDisplay)
                || (!strippedTag.isEmpty() && format.contains(strippedTag))
                || format.contains("[" + activeTag + "]")) {
            return;
        }

        // Escape % in tag to avoid breaking String.format in Bukkit
        String tagToInsert = " " + coloredTag.replace("%", "%%");
        String newFormat = null;

        // Strategy 1: Standard Bukkit format containing %1$s (player display name)
        // e.g. "%1$s: %2$s" -> "%1$s &c[WARLORD]: %2$s"
        if (format.contains("%1$s")) {
            newFormat = format.replace("%1$s", "%1$s" + tagToInsert);
        }
        // Strategy 2: Chat formatter (like LPC) has replaced player name directly in format string
        // e.g. "DEV AKHILPLAYZYT: %2$s" -> "DEV AKHILPLAYZYT &c[WARLORD]: %2$s"
        else if (format.contains(player.getName())) {
            int nameIdx = format.indexOf(player.getName());
            int endIdx = nameIdx + player.getName().length();
            newFormat = format.substring(0, endIdx) + tagToInsert + format.substring(endIdx);
        }
        // Strategy 3: Check player display name if different from player.getName()
        else if (format.contains(player.getDisplayName())) {
            int nameIdx = format.indexOf(player.getDisplayName());
            int endIdx = nameIdx + player.getDisplayName().length();
            newFormat = format.substring(0, endIdx) + tagToInsert + format.substring(endIdx);
        }
        // Strategy 4: Case-insensitive player name match
        else {
            int nameIdx = format.toLowerCase().indexOf(player.getName().toLowerCase());
            if (nameIdx != -1) {
                int endIdx = nameIdx + player.getName().length();
                newFormat = format.substring(0, endIdx) + tagToInsert + format.substring(endIdx);
            }
        }

        if (newFormat != null) {
            event.setFormat(newFormat);
            if (plugin.isDebugCosmeticsEnabled()) {
                plugin.getLogger().info("[DEBUG Cosmetics] Legacy AsyncPlayerChatEvent: injected tag " + activeTag + " into format: '" + newFormat + "' for " + player.getName());
            }
        }
    }
}
