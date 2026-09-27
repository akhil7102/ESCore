package fun.endcore.escoins.tags;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.cosmetics.CosmeticCommandHandler;
import fun.endcore.escoins.cosmetics.CosmeticEntry;
import fun.endcore.escoins.cosmetics.CosmeticManager;
import fun.endcore.escoins.cosmetics.CosmeticType;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Command executor and tab completer for /tag, /tags, and /playertag.
 * Allows players to toggle and equip tags, and administrators to grant/remove them.
 */
public class TagCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;
    private final CosmeticCommandHandler handler;

    public TagCommand(ESCoins plugin, CosmeticCommandHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        // 1. Check for administrative subcommands (give, remove, take, clear)
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                if (!handler.hasPermission(sender)) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                return handler.handleGive(sender, args, CosmeticType.TAG);
            } else if (sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                if (!handler.hasPermission(sender)) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                return handler.handleRemove(sender, args, CosmeticType.TAG);
            }
        }

        // 2. Ensure sender is a player for personal toggle/equip operations
        if (!(sender instanceof Player player)) {
            if (handler.hasPermission(sender)) {
                sendHelp(sender);
            } else {
                mm.sendMessage(sender, "player-only", "{PREFIX}&cThis command can only be executed by players.");
            }
            return true;
        }

        // 3. Permission check for toggling tags (default is true for all players)
        if (!player.hasPermission("escore.tag.toggle") && !player.hasPermission("escore.tags.toggle") && !player.hasPermission("escoins.admin") && !player.isOp()) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        TagManager tm = plugin.getTagManager();
        CosmeticManager cm = plugin.getCosmeticManager();
        if (tm == null || cm == null) {
            mm.sendMessage(player, "tags.disabled-server", "{PREFIX}&cTags are currently disabled on this server.");
            return true;
        }

        // 4. Handle no arguments: /tag
        if (args.length == 0) {
            Optional<CosmeticEntry> optTag = cm.getCosmetic(player.getUniqueId(), CosmeticType.TAG);
            if (optTag.isEmpty() || optTag.get().isExpired()) {
                mm.sendMessage(player, "tags.no-tag", "{PREFIX}&cYou do not have a tag unlocked to toggle.");
                return true;
            }

            CosmeticEntry tag = optTag.get();
            if (tag.active()) {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.TAG, false);
                mm.sendMessage(player, "tags.disabled", "{PREFIX}&cYour tag has been &c&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.TAG, true);
                mm.sendMessage(player, "tags.enabled", "{PREFIX}&aYour tag has been &a&lenabled&a.");
            }
            return true;
        }

        String action = args[0].toLowerCase();

        // 5. Handle toggle actions: on, off, toggle
        if (action.equals("on") || action.equals("enable")) {
            Optional<CosmeticEntry> optTag = cm.getCosmetic(player.getUniqueId(), CosmeticType.TAG);
            if (optTag.isEmpty() || optTag.get().isExpired()) {
                mm.sendMessage(player, "tags.no-tag", "{PREFIX}&cYou do not have a tag unlocked to toggle.");
                return true;
            }
            CosmeticEntry tag = optTag.get();
            if (tag.active()) {
                mm.sendMessage(player, "tags.already-enabled", "{PREFIX}&cYour tag is already &e&lenabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.TAG, true);
                mm.sendMessage(player, "tags.enabled", "{PREFIX}&aYour tag has been &a&lenabled&a.");
            }
            return true;
        }

        if (action.equals("off") || action.equals("disable")) {
            Optional<CosmeticEntry> optTag = cm.getCosmetic(player.getUniqueId(), CosmeticType.TAG);
            if (optTag.isEmpty() || optTag.get().isExpired()) {
                mm.sendMessage(player, "tags.no-tag", "{PREFIX}&cYou do not have a tag unlocked to toggle.");
                return true;
            }
            CosmeticEntry tag = optTag.get();
            if (!tag.active()) {
                mm.sendMessage(player, "tags.already-disabled", "{PREFIX}&cYour tag is already &e&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.TAG, false);
                mm.sendMessage(player, "tags.disabled", "{PREFIX}&cYour tag has been &c&ldisabled&c.");
            }
            return true;
        }

        if (action.equals("toggle")) {
            Optional<CosmeticEntry> optTag = cm.getCosmetic(player.getUniqueId(), CosmeticType.TAG);
            if (optTag.isEmpty() || optTag.get().isExpired()) {
                mm.sendMessage(player, "tags.no-tag", "{PREFIX}&cYou do not have a tag unlocked to toggle.");
                return true;
            }
            CosmeticEntry tag = optTag.get();
            if (tag.active()) {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.TAG, false);
                mm.sendMessage(player, "tags.disabled", "{PREFIX}&cYour tag has been &c&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.TAG, true);
                mm.sendMessage(player, "tags.enabled", "{PREFIX}&aYour tag has been &a&lenabled&a.");
            }
            return true;
        }

        // 6. Handle list: /tag list
        if (action.equals("list")) {
            handleList(player);
            return true;
        }

        // 7. Handle set / equip: /tag set <tag> or /tag equip <tag>
        if (action.equals("set") || action.equals("equip") || action.equals("use")) {
            if (args.length < 2) {
                mm.sendMessage(player, "tags.set-usage", "{PREFIX}&cUsage: /tag set <tag_id>");
                return true;
            }
            handleEquipTag(player, args[1].toLowerCase());
            return true;
        }

        // 8. If arg[0] directly matches a tag ID: e.g. /tag lucky
        if (tm.isValidTag(action)) {
            handleEquipTag(player, action);
            return true;
        }

        // Unknown argument: show usage (or admin help)
        if (handler.hasPermission(player)) {
            sendHelp(player);
        } else {
            mm.sendMessage(player, "tags.usage", "{PREFIX}&cUsage: /tag <on|off|toggle|list|set <tag>>");
        }
        return true;
    }

    private void handleEquipTag(Player player, String tagId) {
        MessageManager mm = plugin.getMessageManager();
        TagManager tm = plugin.getTagManager();
        CosmeticManager cm = plugin.getCosmeticManager();

        TagDefinition def = tm.getTag(tagId);
        if (def == null) {
            String available = String.join(", ", tm.getAllTagIds());
            mm.sendMessage(player, "tags.invalid-tag",
                    "{PREFIX}&cInvalid tag '&e{TAG}&c'. Available tags: &7{TAGS}",
                    "{TAG}", tagId,
                    "{TAGS}", available);
            return;
        }

        if (!tm.hasTagAccess(player, tagId)) {
            mm.sendMessage(player, "tags.not-owned",
                    "{PREFIX}&cYou do not have access to the '&e{TAG}&c' tag.",
                    "{TAG}", tagId);
            return;
        }

        // Equip as permanent entitlement for the player and activate
        cm.givePermanentCosmetic(player.getUniqueId(), CosmeticType.TAG, tagId).thenAccept(success -> {
            if (success) {
                mm.sendMessage(player, "tags.selected",
                        "{PREFIX}&aSelected &r{TAG_DISPLAY} &aas your active tag!",
                        "{TAG_DISPLAY}", def.getFormatted(),
                        "{TAG_NAME}", def.id());
            } else {
                mm.sendMessage(player, "cosmetics.error", "{PREFIX}&cFailed to equip tag.");
            }
        });
    }

    private void handleList(Player player) {
        MessageManager mm = plugin.getMessageManager();
        TagManager tm = plugin.getTagManager();
        String activeTag = tm.getActiveTag(player.getUniqueId());

        mm.sendMessage(player, "tags.list-header", "&8&m----------------&r &b&lAvailable Tags &8&m----------------");
        for (TagDefinition def : tm.getTagsSorted()) {
            boolean isActive = def.id().equalsIgnoreCase(activeTag);
            boolean hasAccess = tm.hasTagAccess(player, def.id());

            if (isActive) {
                mm.sendMessage(player, "tags.list-entry-active",
                        "&8- &r{TAG_DISPLAY} &7(&e{TAG_NAME}&7) &a&l[ACTIVE]",
                        "{TAG_DISPLAY}", def.getFormatted(),
                        "{TAG_NAME}", def.id());
            } else if (hasAccess) {
                mm.sendMessage(player, "tags.list-entry-owned",
                        "&8- &r{TAG_DISPLAY} &7(&e{TAG_NAME}&7) &a✔",
                        "{TAG_DISPLAY}", def.getFormatted(),
                        "{TAG_NAME}", def.id());
            } else {
                mm.sendMessage(player, "tags.list-entry-locked",
                        "&8- &r{TAG_DISPLAY} &7(&e{TAG_NAME}&7) &c✖",
                        "{TAG_DISPLAY}", def.getFormatted(),
                        "{TAG_NAME}", def.id());
            }
        }
        mm.sendMessage(player, "tags.list-footer", "&8&m------------------------------------------------");
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "core.header", "&8&m----------------&r &b&lESCore Tags &8&m----------------");
        mm.sendMessage(sender, "tags.help-onoff", "&e/tag <on|off|toggle> &7- Toggle your active tag on or off");
        mm.sendMessage(sender, "tags.help-list", "&e/tag list &7- View all available tags");
        mm.sendMessage(sender, "tags.help-set", "&e/tag set <tag> &7- Equip a tag you have unlocked");
        if (handler.hasPermission(sender)) {
            mm.sendMessage(sender, "tags.help-give", "&e/tag give <player> <tag_id> <perm|temp <duration>> &7- Grant a tag to a player");
            mm.sendMessage(sender, "tags.help-remove", "&e/tag remove <player> [tag_id] &7- Remove a tag from a player");
        }
        mm.sendMessage(sender, "core.footer", "&8&m----------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("on");
            options.add("off");
            options.add("toggle");
            options.add("list");
            options.add("set");
            if (handler.hasPermission(sender)) {
                options.add("give");
                options.add("remove");
            }

            // Also include accessible tags if sender is a player
            if (sender instanceof Player player && plugin.getTagManager() != null) {
                for (String tagId : plugin.getTagManager().getAllTagIds()) {
                    if (plugin.getTagManager().hasTagAccess(player, tagId)) {
                        options.add(tagId);
                    }
                }
            }

            String current = args[0].toLowerCase();
            return options.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
        }

        // Delegate admin give/remove
        if (args.length > 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                if (handler.hasPermission(sender)) {
                    return handler.onTabComplete(sender, args, CosmeticType.TAG);
                }
                return List.of();
            }

            if (sub.equals("set") || sub.equals("equip") || sub.equals("use")) {
                if (args.length == 2 && sender instanceof Player player && plugin.getTagManager() != null) {
                    List<String> list = new ArrayList<>();
                    for (String tagId : plugin.getTagManager().getAllTagIds()) {
                        if (plugin.getTagManager().hasTagAccess(player, tagId) || player.isOp()) {
                            list.add(tagId);
                        }
                    }
                    String current = args[1].toLowerCase();
                    return list.stream().filter(s -> s.startsWith(current)).toList();
                }
            }
        }

        return List.of();
    }
}
