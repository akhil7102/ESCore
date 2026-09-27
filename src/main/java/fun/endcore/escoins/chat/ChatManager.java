package fun.endcore.escoins.chat;

import fun.endcore.escoins.ESCoins;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages chat formatting, hover tooltips, click events, and placeholder resolution.
 */
public class ChatManager {
    private final ESCoins plugin;

    private boolean enabled = true;
    private String format = "{PREFIX}&f{PLAYER} &7▶ &f{MESSAGE}";
    private String defaultPrefix = "&f&lMEMBER ";
    private String clickAction = "SUGGEST_COMMAND";
    private String clickValue = "/msg {PLAYER} ";
    private boolean hoverEnabled = true;
    private List<String> hoverLines = new ArrayList<>();

    private static volatile Method cachedVaultPrefixMethod;
    private static volatile boolean vaultLookupAttempted = false;

    public ChatManager(ESCoins plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        this.enabled = config.getBoolean("chat.enabled", true);
        this.format = config.getString("chat.format", "{PREFIX}&f{PLAYER} &7▶ &f{MESSAGE}");
        this.defaultPrefix = config.getString("chat.default-prefix", "&f&lMEMBER ");
        this.clickAction = config.getString("chat.click.action", "SUGGEST_COMMAND");
        this.clickValue = config.getString("chat.click.value", "/msg {PLAYER} ");
        this.hoverEnabled = config.getBoolean("chat.hover.enabled", true);
        this.hoverLines = config.getStringList("chat.hover.lines");

        if (this.hoverLines.isEmpty()) {
            this.hoverLines = List.of(
                    "{PREFIX}&f{PLAYER}",
                    "&a$ &fMoney: &a{MONEY}",
                    "&b⚑ &fTeam: &b{TEAM}",
                    "&c⚔ &fKills: &c{KILLS}",
                    "&6☠ &fDeaths: &6{DEATHS}",
                    "&e🕒 &fPlaytime: &e{PLAYTIME}",
                    "&bᯤ &fPing &b{PING}ms",
                    "",
                    "&e➡ &6&l&nCLICK&r &6to Message!"
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Renders a full chat message component with interactive hover tooltips and click events.
     */
    public Component renderChatMessage(Player source, Component message) {
        if (!enabled) {
            return Component.text("<" + source.getName() + "> ").append(message);
        }

        String prefix = resolvePrefix(source);
        String playerName = source.getName();

        // 1. Build name tag (Prefix + Player Name)
        Component nameTag = plugin.getMessageManager().parse(prefix + "&f" + playerName);

        // 2. Attach hover tooltip
        if (hoverEnabled) {
            Component hoverComp = buildHoverComponent(source, prefix);
            nameTag = nameTag.hoverEvent(HoverEvent.showText(hoverComp));
        }

        // 3. Attach click action
        if (clickValue != null && !clickValue.isEmpty()) {
            String command = clickValue.replace("{PLAYER}", playerName);
            if ("RUN_COMMAND".equalsIgnoreCase(clickAction)) {
                nameTag = nameTag.clickEvent(ClickEvent.runCommand(command));
            } else {
                nameTag = nameTag.clickEvent(ClickEvent.suggestCommand(command));
            }
        }

        // 4. Player Tag (appears after username if active)
        Component tagComp = Component.empty();
        if (plugin.getTagManager() != null) {
            String activeTagDisplay = plugin.getTagManager().getActiveTagDisplay(source.getUniqueId());
            if (activeTagDisplay != null && !activeTagDisplay.isEmpty()) {
                tagComp = Component.space().append(plugin.getMessageManager().parse(activeTagDisplay));
            }
        }

        // 5. Separator
        Component separator = plugin.getMessageManager().parse(" &7▶ ");

        // 6. Message text
        String rawText = PlainTextComponentSerializer.plainText().serialize(message);
        Component messageComp;

        // Resolve active chat color cosmetic if available
        fun.endcore.escoins.cosmetics.CosmeticColor activeColor = null;
        if (plugin.getCosmeticManager() != null && plugin.getCosmeticManager().isChatColorEnabled()) {
            java.util.Optional<fun.endcore.escoins.cosmetics.CosmeticEntry> optColor =
                    plugin.getCosmeticManager().getActiveCosmetic(source.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR);
            if (optColor.isPresent()) {
                activeColor = plugin.getCosmeticManager().getChatColor(optColor.get().color());
            }
        }

        if (source.hasPermission("escoins.chat.color") || source.hasPermission("escore.chat.color")) {
            if (activeColor != null) {
                messageComp = plugin.getMessageManager().parse(activeColor.chatCode() + rawText);
            } else {
                messageComp = plugin.getMessageManager().parse(rawText);
            }
        } else {
            if (activeColor != null) {
                messageComp = Component.text(rawText, activeColor.namedTextColor());
            } else {
                messageComp = Component.text(rawText, NamedTextColor.WHITE);
            }
        }

        return Component.empty()
                .append(nameTag)
                .append(tagComp)
                .append(separator)
                .append(messageComp);
    }

    /**
     * Builds the interactive hover tooltip component.
     */
    public Component buildHoverComponent(Player player, String prefix) {
        String playerName = player.getName();
        String money = resolveMoney(player);
        String team = resolveTeam(player);
        int kills = resolveKills(player);
        int deaths = resolveDeaths(player);
        String playtime = resolvePlaytime(player);
        int ping = resolvePing(player);

        Component result = Component.empty();
        boolean first = true;

        for (String rawLine : hoverLines) {
            String line = rawLine;

            // Direct token replacements
            line = line.replace("{PREFIX}", prefix)
                       .replace("{PLAYER}", playerName)
                       .replace("{MONEY}", money)
                       .replace("{TEAM}", team)
                       .replace("{KILLS}", String.valueOf(kills))
                       .replace("{DEATHS}", String.valueOf(deaths))
                       .replace("{PLAYTIME}", playtime)
                       .replace("{PING}", String.valueOf(ping));

            // If PlaceholderAPI is present, parse any remaining placeholders (including %betterteams_name%)
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
            }

            Component lineComp = plugin.getMessageManager().parse(line);
            if (!first) {
                result = result.append(Component.newline());
            }
            result = result.append(lineComp);
            first = false;
        }

        return result;
    }

    /**
     * Resolves the player's team name using %betterteams_name% or fallback.
     */
    public String resolveTeam(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String team = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%betterteams_name%");
                if (team != null && !team.isEmpty() && !team.equals("%betterteams_name%") && !team.equalsIgnoreCase("none")) {
                    return team;
                }
            } catch (Throwable ignored) {}
        }
        return "None";
    }

    /**
     * Resolves player rank/prefix via PlaceholderAPI, Vault Chat, or default config.
     */
    public String resolvePrefix(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String vp = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%vault_prefix%");
                if (vp != null && !vp.isEmpty() && !vp.equals("%vault_prefix%")) {
                    return vp.endsWith(" ") ? vp : vp + " ";
                }
                String lp = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
                if (lp != null && !lp.isEmpty() && !lp.equals("%luckperms_prefix%")) {
                    return lp.endsWith(" ") ? lp : lp + " ";
                }
            } catch (Throwable ignored) {}
        }

        // Try Vault Chat via reflection / ServicesManager
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                if (!vaultLookupAttempted) {
                    try {
                        Class<?> chatClass = Class.forName("net.milkbowl.vault.chat.Chat");
                        cachedVaultPrefixMethod = chatClass.getMethod("getPlayerPrefix", Player.class);
                    } catch (Throwable ignored) {}
                    vaultLookupAttempted = true;
                }
                if (cachedVaultPrefixMethod != null) {
                    Class<?> chatClass = cachedVaultPrefixMethod.getDeclaringClass();
                    RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(chatClass);
                    if (rsp != null && rsp.getProvider() != null) {
                        Object res = cachedVaultPrefixMethod.invoke(rsp.getProvider(), player);
                        if (res instanceof String s && !s.isEmpty()) {
                            return s.endsWith(" ") ? s : s + " ";
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return defaultPrefix;
    }

    /**
     * Resolves the player's server economy money (e.g. EssentialsX / Vault balance).
     */
    public String resolveMoney(Player player) {
        // 1. Try PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%vault_eco_balance_formatted%");
                if (formatted != null && !formatted.isEmpty() && !formatted.equals("%vault_eco_balance_formatted%")) {
                    return formatted;
                }
                String raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%vault_eco_balance%");
                if (raw != null && !raw.isEmpty() && !raw.equals("%vault_eco_balance%")) {
                    try {
                        double val = Double.parseDouble(raw);
                        return val == (long) val ? String.valueOf((long) val) : String.format("%.2f", val);
                    } catch (NumberFormatException ignored) {
                        return raw;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try Vault Economy service via reflection
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
                if (rsp != null && rsp.getProvider() != null) {
                    Method getBalance = rsp.getProvider().getClass().getMethod("getBalance", OfflinePlayer.class);
                    Object res = getBalance.invoke(rsp.getProvider(), player);
                    if (res instanceof Double d) {
                        return d == (long) d.doubleValue() ? String.valueOf((long) d.doubleValue()) : String.format("%.2f", d);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "0";
    }

    /**
     * Resolves player kills via statistics.
     */
    public int resolveKills(Player player) {
        try {
            return player.getStatistic(Statistic.PLAYER_KILLS);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Resolves player deaths via statistics.
     */
    public int resolveDeaths(Player player) {
        try {
            return player.getStatistic(Statistic.DEATHS);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Resolves player playtime in hours (e.g. "46h", "0h").
     */
    public String resolvePlaytime(Player player) {
        try {
            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long hours = ticks / 72000L;
            return hours + "h";
        } catch (Exception e) {
            return "0h";
        }
    }

    /**
     * Resolves player ping in milliseconds.
     */
    public int resolvePing(Player player) {
        try {
            return player.getPing();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<String> getHoverLines() {
        return hoverLines;
    }
}
