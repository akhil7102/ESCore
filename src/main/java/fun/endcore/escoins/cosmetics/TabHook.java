package fun.endcore.escoins.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles seamless integration with nametag and scoreboard plugins (such as TAB by NEZNAMY and NametagEdit).
 *
 * In Minecraft 1.13+, glowing outline color is strictly tied to the client-side Team Color.
 * Plugins like TAB manage scoreboard teams via raw network packets and override Bukkit scoreboard teams,
 * deriving the glowing color from the last color code in the player's tagprefix.
 *
 * This hook automatically ensures the player's glow color code is applied to TAB's nametag pipeline.
 */
public class TabHook {

    private static Boolean tabPluginAvailable = null;
    private static Boolean ntePluginAvailable = null;

    public static boolean isTabAvailable() {
        if (tabPluginAvailable == null) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                    try {
                        Class.forName("me.neznamy.tab.api.TabAPI");
                        tabPluginAvailable = true;
                    } catch (ClassNotFoundException e) {
                        try {
                            Class.forName("me.neznamy.tab.api.TAB");
                            tabPluginAvailable = true;
                        } catch (ClassNotFoundException ignored) {
                            tabPluginAvailable = false;
                        }
                    }
                } else {
                    tabPluginAvailable = false;
                }
            } catch (Throwable t) {
                tabPluginAvailable = false;
            }
        }
        return tabPluginAvailable != null && tabPluginAvailable;
    }

    public static boolean isNteAvailable() {
        if (ntePluginAvailable == null) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("NametagEdit")) {
                    Class.forName("com.nametagedit.plugin.NametagEdit");
                    ntePluginAvailable = true;
                } else {
                    ntePluginAvailable = false;
                }
            } catch (Throwable t) {
                ntePluginAvailable = false;
            }
        }
        return ntePluginAvailable != null && ntePluginAvailable;
    }

    /**
     * Applies the glow color code to the player's nametag in external nametag plugins.
     */
    public static void applyGlow(Player player, CosmeticColor color) {
        if (player == null || color == null) return;

        // 1. TAB by NEZNAMY
        if (isTabAvailable()) {
            applyTabGlow(player, color);
        }

        // 2. NametagEdit
        if (isNteAvailable()) {
            applyNteGlow(player, color);
        }
    }

    /**
     * Resets the player's nametag prefix back to default in external nametag plugins.
     */
    public static void removeGlow(Player player) {
        if (player == null) return;

        // 1. TAB by NEZNAMY
        if (isTabAvailable()) {
            removeTabGlow(player);
        }

        // 2. NametagEdit
        if (isNteAvailable()) {
            removeNteGlow(player);
        }
    }

    private static void applyTabGlow(Player player, CosmeticColor color) {
        try {
            Class<?> tabApiClass = null;
            try {
                tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            } catch (ClassNotFoundException e) {
                try {
                    tabApiClass = Class.forName("me.neznamy.tab.api.TAB");
                } catch (ClassNotFoundException ignored) {}
            }
            if (tabApiClass == null) return;

            Method getInstanceMethod = tabApiClass.getMethod("getInstance");
            Object tabApi = getInstanceMethod.invoke(null);
            if (tabApi == null) return;

            Class<?> tabPlayerClass = null;
            try {
                tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            } catch (ClassNotFoundException ignored) {}

            // Resolve TabPlayer by UUID or name
            Object tabPlayer = null;
            try {
                Method getPlayerByUuid = tabApi.getClass().getMethod("getPlayer", UUID.class);
                tabPlayer = getPlayerByUuid.invoke(tabApi, player.getUniqueId());
            } catch (Throwable ignored) {}

            if (tabPlayer == null) {
                try {
                    Method getPlayerByName = tabApi.getClass().getMethod("getPlayer", String.class);
                    tabPlayer = getPlayerByName.invoke(tabApi, player.getName());
                } catch (Throwable ignored) {}
            }

            if (tabPlayer == null) return;

            // Resolve NameTagManager or TeamManager
            Object nameTagManager = null;
            try {
                Method getNameTagManagerMethod = tabApi.getClass().getMethod("getNameTagManager");
                nameTagManager = getNameTagManagerMethod.invoke(tabApi);
            } catch (Throwable ignored) {}

            if (nameTagManager == null) {
                try {
                    Method getTeamManagerMethod = tabApi.getClass().getMethod("getTeamManager");
                    nameTagManager = getTeamManagerMethod.invoke(tabApi);
                } catch (Throwable ignored) {}
            }

            if (nameTagManager == null) return;

            Class<?> targetParamClass = tabPlayerClass != null ? tabPlayerClass : tabPlayer.getClass();

            // Retrieve original prefix from TAB
            String originalPrefix = "";
            try {
                Method getOriginalMethod = nameTagManager.getClass().getMethod("getOriginalRawPrefix", targetParamClass);
                Object raw = getOriginalMethod.invoke(nameTagManager, tabPlayer);
                if (raw != null) {
                    originalPrefix = (String) raw;
                }
            } catch (Throwable e) {
                try {
                    Method getOriginalMethod = nameTagManager.getClass().getMethod("getOriginalReplacedPrefix", targetParamClass);
                    Object raw = getOriginalMethod.invoke(nameTagManager, tabPlayer);
                    if (raw != null) {
                        originalPrefix = (String) raw;
                    }
                } catch (Throwable ignored) {}
            }

            // If the user already configured TAB's groups.yml with our placeholder, let TAB resolve it natively
            if (originalPrefix.contains("%escore_glow_code%") || originalPrefix.contains("%escore_playerglow_code%")) {
                return;
            }

            // Strip any previous trailing glow color code if present, then append current glow color code
            String cleanedPrefix = stripTrailingColorCodes(originalPrefix);
            String newPrefix;
            if (cleanedPrefix.isEmpty()) {
                newPrefix = color.chatCode();
            } else if (cleanedPrefix.endsWith(" ")) {
                newPrefix = cleanedPrefix + color.chatCode();
            } else {
                newPrefix = cleanedPrefix + " " + color.chatCode();
            }

            Method setPrefixMethod = nameTagManager.getClass().getMethod("setPrefix", targetParamClass, String.class);
            setPrefixMethod.invoke(nameTagManager, tabPlayer, newPrefix);

        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[ESCore] Failed to update TAB nametag for glow", t);
        }
    }

    private static void removeTabGlow(Player player) {
        try {
            Class<?> tabApiClass = null;
            try {
                tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            } catch (ClassNotFoundException e) {
                try {
                    tabApiClass = Class.forName("me.neznamy.tab.api.TAB");
                } catch (ClassNotFoundException ignored) {}
            }
            if (tabApiClass == null) return;

            Method getInstanceMethod = tabApiClass.getMethod("getInstance");
            Object tabApi = getInstanceMethod.invoke(null);
            if (tabApi == null) return;

            Class<?> tabPlayerClass = null;
            try {
                tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            } catch (ClassNotFoundException ignored) {}

            Object tabPlayer = null;
            try {
                Method getPlayerByUuid = tabApi.getClass().getMethod("getPlayer", UUID.class);
                tabPlayer = getPlayerByUuid.invoke(tabApi, player.getUniqueId());
            } catch (Throwable ignored) {}

            if (tabPlayer == null) {
                try {
                    Method getPlayerByName = tabApi.getClass().getMethod("getPlayer", String.class);
                    tabPlayer = getPlayerByName.invoke(tabApi, player.getName());
                } catch (Throwable ignored) {}
            }

            if (tabPlayer == null) return;

            Object nameTagManager = null;
            try {
                Method getNameTagManagerMethod = tabApi.getClass().getMethod("getNameTagManager");
                nameTagManager = getNameTagManagerMethod.invoke(tabApi);
            } catch (Throwable ignored) {}

            if (nameTagManager == null) {
                try {
                    Method getTeamManagerMethod = tabApi.getClass().getMethod("getTeamManager");
                    nameTagManager = getTeamManagerMethod.invoke(tabApi);
                } catch (Throwable ignored) {}
            }

            if (nameTagManager == null) return;

            Class<?> targetParamClass = tabPlayerClass != null ? tabPlayerClass : tabPlayer.getClass();

            // Setting prefix to null in TAB resets custom nametag to the group default
            Method setPrefixMethod = nameTagManager.getClass().getMethod("setPrefix", targetParamClass, String.class);
            setPrefixMethod.invoke(nameTagManager, tabPlayer, (String) null);

        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[ESCore] Failed to reset TAB nametag for glow", t);
        }
    }

    private static void applyNteGlow(Player player, CosmeticColor color) {
        try {
            Class<?> nteClass = Class.forName("com.nametagedit.plugin.NametagEdit");
            Object api = nteClass.getMethod("getApi").invoke(null);
            if (api == null) return;

            // Get current prefix and append color code
            Method getNametagMethod = api.getClass().getMethod("getNametag", Player.class);
            Object nametag = getNametagMethod.invoke(api, player);
            String currentPrefix = "";
            if (nametag != null) {
                Method getPrefixMethod = nametag.getClass().getMethod("getPrefix");
                Object p = getPrefixMethod.invoke(nametag);
                if (p != null) currentPrefix = (String) p;
            }

            String cleanedPrefix = stripTrailingColorCodes(currentPrefix);
            String newPrefix = cleanedPrefix.endsWith(" ") ? (cleanedPrefix + color.chatCode()) : (cleanedPrefix + " " + color.chatCode());

            Method setPrefixMethod = api.getClass().getMethod("setPrefix", Player.class, String.class);
            setPrefixMethod.invoke(api, player, newPrefix);

        } catch (Throwable ignored) {}
    }

    private static void removeNteGlow(Player player) {
        try {
            Class<?> nteClass = Class.forName("com.nametagedit.plugin.NametagEdit");
            Object api = nteClass.getMethod("getApi").invoke(null);
            if (api == null) return;

            Method reloadMethod = api.getClass().getMethod("reloadNametag", Player.class);
            reloadMethod.invoke(api, player);

        } catch (Throwable ignored) {}
    }

    private static String stripTrailingColorCodes(String str) {
        if (str == null || str.isEmpty()) return "";
        String result = str;
        boolean changed = true;
        while (changed) {
            changed = false;
            int len = result.length();
            if (len >= 2) {
                char c1 = result.charAt(len - 2);
                char c2 = result.charAt(len - 1);
                if ((c1 == '&' || c1 == '§') && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(c2) >= 0) {
                    result = result.substring(0, len - 2);
                    changed = true;
                }
            }
        }
        return result;
    }
}
