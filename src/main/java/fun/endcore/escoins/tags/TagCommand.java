package fun.endcore.escoins.tags;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Command executor and tab completer for player tag commands: /tags, /tags list, /tags select, /tags remove.
 */
public class TagCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;

    public TagCommand(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!(sender instanceof Player player)) {
            mm.sendMessage(sender, "player-only", "{PREFIX}&cThis command can only be executed by players.");
            return true;
        }

        if (!player.hasPermission("escore.tags")) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        if (args.length == 0) {
            handleStatusAndHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(player);
            case "select", "equip", "use", "set" -> handleSelect(player, args);
            case "remove", "unequip", "clear", "reset" -> handleRemove(player);
            default -> handleStatusAndHelp(player);
        }

        return true;
    }

    private void handleStatusAndHelp(Player player) {
        MessageManager mm = plugin.getMessageManager();
        TagManager tm = plugin.getTagManager();
        PlayerTagData data = tm.getPlayerTags(player.getUniqueId());

        String active = data.getActiveTag();
        String activeDisplay = "None";
        if (active != null) {
            TagDefinition def = tm.getTag(active);
            if (def != null) {
                activeDisplay = def.display();
            }
        }

        mm.sendMessage(player, "tags.header", "&8&m----------------&r &b&lPlayer Tags &8&m----------------");
        mm.sendMessage(player, "tags.status", "&7Current Tag: &r{TAG_DISPLAY} &7({TAG_NAME})",
                "{TAG_DISPLAY}", activeDisplay,
                "{TAG_NAME}", active != null ? active : "None");
        mm.sendMessage(player, "tags.owned-count", "&7Owned Tags: &e{COUNT}",
                "{COUNT}", String.valueOf(data.getOwnedTags().size()));
        mm.sendMessage(player, "tags.help-list", "&e/tags list &7- View your owned tags");
        mm.sendMessage(player, "tags.help-select", "&e/tags select <tag> &7- Select active chat tag");
        mm.sendMessage(player, "tags.help-remove", "&e/tags remove &7- Remove active chat tag");
        mm.sendMessage(player, "tags.footer", "&8&m----------------------------------------");
    }

    private void handleList(Player player) {
        MessageManager mm = plugin.getMessageManager();

        if (!player.hasPermission("escore.tags.list") && !player.hasPermission("escore.tags")) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return;
        }

        TagManager tm = plugin.getTagManager();
        PlayerTagData data = tm.getPlayerTags(player.getUniqueId());
        Set<String> owned = data.getOwnedTags();

        if (owned.isEmpty()) {
            mm.sendMessage(player, "tags.list-empty", "{PREFIX}&cYou do not own any tags.");
            return;
        }

        String active = data.getActiveTag();
        mm.sendMessage(player, "tags.list-header", "&8&m----------------&r &b&lYour Owned Tags &8&m----------------");
        for (String tagId : owned) {
            TagDefinition def = tm.getTag(tagId);
            String display = def != null ? def.display() : "[" + tagId + "]";
            boolean isActive = tagId.equalsIgnoreCase(active);
            if (isActive) {
                mm.sendMessage(player, "tags.list-entry-active", "&8- &r{TAG_DISPLAY} &7(&e{TAG_NAME}&7) &a&l[ACTIVE]",
                        "{TAG_DISPLAY}", display,
                        "{TAG_NAME}", tagId);
            } else {
                mm.sendMessage(player, "tags.list-entry", "&8- &r{TAG_DISPLAY} &7(&e{TAG_NAME}&7)",
                        "{TAG_DISPLAY}", display,
                        "{TAG_NAME}", tagId);
            }
        }
        mm.sendMessage(player, "tags.list-footer", "&8&m------------------------------------------------");
    }

    private void handleSelect(Player player, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!player.hasPermission("escore.tags.select") && !player.hasPermission("escore.tags")) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return;
        }

        if (args.length < 2) {
            mm.sendMessage(player, "tags.select-usage", "{PREFIX}&cUsage: /tags select <tag>");
            return;
        }

        String targetTag = args[1].trim().toUpperCase();
        TagManager tm = plugin.getTagManager();

        if (!tm.isValidTag(targetTag)) {
            mm.sendMessage(player, "tags.not-found", "{PREFIX}&cTag '&e{TAG}&c' does not exist.",
                    "{TAG}", targetTag);
            return;
        }

        if (!tm.hasTag(player.getUniqueId(), targetTag)) {
            mm.sendMessage(player, "tags.not-owned", "{PREFIX}&cYou do not own the '&e{TAG}&c' tag.",
                    "{TAG}", targetTag);
            return;
        }

        tm.setActiveTag(player.getUniqueId(), targetTag).thenAccept(success -> {
            if (success) {
                TagDefinition def = tm.getTag(targetTag);
                String display = def != null ? def.display() : targetTag;
                mm.sendMessage(player, "tags.selected", "{PREFIX}&aSelected &r{TAG_DISPLAY} &aas your active tag!",
                        "{TAG_DISPLAY}", display,
                        "{TAG_NAME}", targetTag);
            } else {
                mm.sendMessage(player, "tags.error", "{PREFIX}&cFailed to set active tag.");
            }
        });
    }

    private void handleRemove(Player player) {
        MessageManager mm = plugin.getMessageManager();

        if (!player.hasPermission("escore.tags.remove") && !player.hasPermission("escore.tags")) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return;
        }

        TagManager tm = plugin.getTagManager();
        String active = tm.getActiveTag(player.getUniqueId());
        if (active == null) {
            mm.sendMessage(player, "tags.none-active", "{PREFIX}&cYou do not have an active tag selected.");
            return;
        }

        tm.setActiveTag(player.getUniqueId(), null).thenAccept(success -> {
            if (success) {
                mm.sendMessage(player, "tags.removed", "{PREFIX}&aRemoved your active tag. It will no longer appear in chat.");
            } else {
                mm.sendMessage(player, "tags.error", "{PREFIX}&cFailed to remove active tag.");
            }
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (player.hasPermission("escore.tags.list") || player.hasPermission("escore.tags")) {
                options.add("list");
            }
            if (player.hasPermission("escore.tags.select") || player.hasPermission("escore.tags")) {
                options.add("select");
            }
            if (player.hasPermission("escore.tags.remove") || player.hasPermission("escore.tags")) {
                options.add("remove");
            }
            String current = args[0].toLowerCase();
            return options.stream().filter(s -> s.startsWith(current)).toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("select") || args[0].equalsIgnoreCase("equip"))) {
            PlayerTagData data = plugin.getTagManager().getPlayerTags(player.getUniqueId());
            String current = args[1].toLowerCase();
            return data.getOwnedTags().stream()
                    .filter(t -> t.toLowerCase().startsWith(current))
                    .toList();
        }

        return List.of();
    }
}
