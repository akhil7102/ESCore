package fun.endcore.escoins;

import fun.endcore.escoins.api.ESCoinsAPI;
import fun.endcore.escoins.api.ESCoinsAPIImpl;
import fun.endcore.escoins.api.ESCoinsAPIProvider;
import fun.endcore.escoins.clearlag.ClearLagManager;
import fun.endcore.escoins.command.*;
import fun.endcore.escoins.config.ConfigManager;
import fun.endcore.escoins.database.DatabaseManager;
import fun.endcore.escoins.database.DatabaseType;
import fun.endcore.escoins.database.MySQLDatabase;
import fun.endcore.escoins.database.SQLiteDatabase;
import fun.endcore.escoins.economy.CoinLeaderboard;
import fun.endcore.escoins.economy.CoinManager;
import fun.endcore.escoins.listener.PlayerConnectionListener;
import fun.endcore.escoins.placeholder.ESCoinsExpansion;
import fun.endcore.escoins.placeholder.ESCoreCoinsExpansion;
import fun.endcore.escoins.spawn.SpawnManager;
import fun.endcore.escoins.util.MessageManager;
import fun.endcore.escoins.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import java.util.logging.Level;

/**
 * ESCore / ESCoins - Core Server Management System for EndCore Lifesteal.
 */
public class ESCoins extends JavaPlugin {
    private static ESCoins instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private CoinLeaderboard leaderboard;
    private CoinManager coinManager;
    private ClearLagManager clearLagManager;
    private SpawnManager spawnManager;
    private fun.endcore.escoins.chat.ChatManager chatManager;
    private fun.endcore.escoins.arena.ArenaManager arenaManager;
    private fun.endcore.escoins.cosmetics.CosmeticManager cosmeticManager;
    private fun.endcore.escoins.tags.TagManager tagManager;
    private fun.endcore.escoins.update.UpdateChecker updateChecker;
    private ESCoinsAPI api;

    private ESCoreCoinsExpansion escoreExpansion;
    private ESCoinsExpansion escoinsExpansion;

    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();

        getLogger().info("Initializing ESCore v" + getDescription().getVersion() + " by " + getDescription().getAuthors() + "...");

        try {
            // 1. Configurations
            this.configManager = new ConfigManager(this);
            this.messageManager = new MessageManager(this);

            // Configure number formatting
            String sep = configManager.getConfig().getString("formatting.thousands-separator", ",");
            int groupSize = configManager.getConfig().getInt("formatting.grouping-size", 3);
            NumberFormatter.configure(sep, groupSize);

            // 2. Database Initialization
            initializeDatabase();

            // 3. Economy and Leaderboard
            this.leaderboard = new CoinLeaderboard(this);
            this.coinManager = new CoinManager(this);

            // 4. Spawn Management System
            this.spawnManager = new SpawnManager(this);

            // 5. API Registration
            this.api = new ESCoinsAPIImpl(this.coinManager);
            ESCoinsAPIProvider.register(this.api);

            // 6. Player Tags System
            this.tagManager = new fun.endcore.escoins.tags.TagManager(this);

            // 7. Cosmetic Entitlements System (Chat Colors, Player Glow & Tags)
            this.cosmeticManager = new fun.endcore.escoins.cosmetics.CosmeticManager(this);

            // 7. Commands Registration
            registerCommands();

            // 9. Chat Management System
            this.chatManager = new fun.endcore.escoins.chat.ChatManager(this);

            // 8. Arena Regeneration System
            this.arenaManager = new fun.endcore.escoins.arena.ArenaManager(this);

            // 9. Listeners Registration
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
            getServer().getPluginManager().registerEvents(new fun.endcore.escoins.listener.DeathMessagesListener(this), this);
            getServer().getPluginManager().registerEvents(new fun.endcore.escoins.listener.ChatListener(this), this);
            getServer().getPluginManager().registerEvents(new fun.endcore.escoins.arena.ArenaWandListener(this), this);

            // 10. ClearLag Initialization
            this.clearLagManager = new ClearLagManager(this);

            // 9. PlaceholderAPI Hook
            registerPlaceholders();

            // 10. Cache online players if server was reloaded
            for (Player player : Bukkit.getOnlinePlayers()) {
                coinManager.onPlayerJoin(player.getUniqueId(), player.getName());
            }

            // 11. bStats Metrics Initialization
            int pluginId = 34282;
            new Metrics(this, pluginId);

            // 12. BuiltByBit Update Notifier
            this.updateChecker = new fun.endcore.escoins.update.UpdateChecker(this);
            this.updateChecker.start();

            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("ESCore v" + getDescription().getVersion() + " enabled successfully in " + elapsed + "ms!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to properly enable ESCore. Disabling plugin...", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling ESCore...");

        // Unregister Placeholders
        if (escoreExpansion != null) {
            escoreExpansion.unregister();
            escoreExpansion = null;
        }
        if (escoinsExpansion != null) {
            escoinsExpansion.unregister();
            escoinsExpansion = null;
        }

        // Stop ClearLag
        if (clearLagManager != null) {
            clearLagManager.stop();
            clearLagManager = null;
        }

        // Stop Leaderboard
        if (leaderboard != null) {
            leaderboard.stop();
            leaderboard = null;
        }

        // Save Arena Manager
        if (arenaManager != null) {
            arenaManager.saveAll();
            arenaManager = null;
        }

        // Stop Cosmetics
        if (cosmeticManager != null) {
            cosmeticManager.stop();
            cosmeticManager = null;
        }

        // Stop Tags
        if (tagManager != null) {
            tagManager.stop();
            tagManager = null;
        }

        // Stop Update Checker
        if (updateChecker != null) {
            updateChecker.stop();
            updateChecker = null;
        }

        // Unregister API
        ESCoinsAPIProvider.unregister();

        // Close Database
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }

        instance = null;
        getLogger().info("ESCore disabled cleanly.");
    }

    private void initializeDatabase() throws Exception {
        String typeStr = configManager.getDatabase().getString("database.type", "SQLITE");
        DatabaseType type = DatabaseType.fromString(typeStr);

        if (type == DatabaseType.MYSQL || type == DatabaseType.MARIADB) {
            this.databaseManager = new MySQLDatabase(this);
        } else {
            String fileName = configManager.getDatabase().getString("database.sqlite.file", "coins.db");
            this.databaseManager = new SQLiteDatabase(this, fileName);
        }

        this.databaseManager.initialize();
    }

    private void registerCommands() {
        // /escore
        ESCoreCommand escoreCommand = new ESCoreCommand(this);
        PluginCommand escore = getCommand("escore");
        if (escore != null) {
            escore.setExecutor(escoreCommand);
            escore.setTabCompleter(escoreCommand);
        }

        // /cc (chatcolor shortcut alias)
        fun.endcore.escoins.cosmetics.ChatColorCommand ccCommand =
                new fun.endcore.escoins.cosmetics.ChatColorCommand(this, escoreCommand.getCosmeticHandler());
        PluginCommand cc = getCommand("cc");
        if (cc != null) {
            cc.setExecutor(ccCommand);
            cc.setTabCompleter(ccCommand);
        }

        // /glow (player glow toggle command)
        fun.endcore.escoins.cosmetics.GlowCommand glowCommand =
                new fun.endcore.escoins.cosmetics.GlowCommand(this, escoreCommand.getCosmeticHandler());
        PluginCommand glow = getCommand("glow");
        if (glow != null) {
            glow.setExecutor(glowCommand);
            glow.setTabCompleter(glowCommand);
        }

        // /tag (player tag toggle and selection command)
        fun.endcore.escoins.tags.TagCommand tagCommand =
                new fun.endcore.escoins.tags.TagCommand(this, escoreCommand.getCosmeticHandler());
        PluginCommand tag = getCommand("tag");
        if (tag != null) {
            tag.setExecutor(tagCommand);
            tag.setTabCompleter(tagCommand);
        }

        // /coins
        CoinsCommand coinsCommand = new CoinsCommand(this);
        PluginCommand coins = getCommand("coins");
        if (coins != null) {
            coins.setExecutor(coinsCommand);
            coins.setTabCompleter(coinsCommand);
        }

        // /clearlag
        ClearLagCommand clearLagCommand = new ClearLagCommand(this);
        PluginCommand clearlag = getCommand("clearlag");
        if (clearlag != null) {
            clearlag.setExecutor(clearLagCommand);
            clearlag.setTabCompleter(clearLagCommand);
        }

        // /spawn
        SpawnCommand spawnCommand = new SpawnCommand(this);
        PluginCommand spawn = getCommand("spawn");
        if (spawn != null) {
            spawn.setExecutor(spawnCommand);
        }

        // /setspawn
        SetSpawnCommand setSpawnCommand = new SetSpawnCommand(this);
        PluginCommand setspawn = getCommand("setspawn");
        if (setspawn != null) {
            setspawn.setExecutor(setSpawnCommand);
        }

        // /arena
        fun.endcore.escoins.arena.ArenaCommand arenaCommand = new fun.endcore.escoins.arena.ArenaCommand(this);
        PluginCommand arena = getCommand("arena");
        if (arena != null) {
            arena.setExecutor(arenaCommand);
            arena.setTabCompleter(arenaCommand);
        }
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                this.escoreExpansion = new ESCoreCoinsExpansion(this);
                this.escoreExpansion.register();

                this.escoinsExpansion = new ESCoinsExpansion(this);
                this.escoinsExpansion.register();

                getLogger().info("Hooked into PlaceholderAPI successfully.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", e);
            }
        }
    }

    public void reloadPlugin() {
        configManager.reload();
        messageManager.reload();

        String sep = configManager.getConfig().getString("formatting.thousands-separator", ",");
        int groupSize = configManager.getConfig().getInt("formatting.grouping-size", 3);
        NumberFormatter.configure(sep, groupSize);

        if (leaderboard != null) {
            leaderboard.reloadConfig();
        }
        if (clearLagManager != null) {
            clearLagManager.reloadConfig();
        }
        if (spawnManager != null) {
            spawnManager.loadConfig();
        }
        if (chatManager != null) {
            chatManager.loadConfig();
        }
        if (arenaManager != null) {
            arenaManager.reload();
        }
        if (cosmeticManager != null) {
            cosmeticManager.reloadConfig();
        }
        if (tagManager != null) {
            tagManager.reloadConfig();
        }
        if (updateChecker != null) {
            updateChecker.reloadConfig();
        }

        getLogger().info("ESCore configuration reloaded.");
    }

    /**
     * Dynamically updates the registration of the /clearlag and /clearentities commands
     * in Bukkit's CommandMap. When disabled, the commands are unregistered so external clear lag
     * plugins can handle them without conflicts.
     *
     * @param enable true to register/enable ESCore's ClearLag commands, false to unregister/release them
     */
    public void updateClearLagCommand(boolean enable) {
        try {
            org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();
            java.util.Map<String, org.bukkit.command.Command> knownCommands = commandMap.getKnownCommands();
            PluginCommand clearlagCmd = getCommand("clearlag");

            String pluginPrefix = getDescription().getName().toLowerCase();

            if (!enable) {
                if (clearlagCmd != null) {
                    clearlagCmd.unregister(commandMap);
                }

                // Look for alternative external clear lag command to restore
                org.bukkit.command.Command fallbackCmd = null;
                org.bukkit.command.Command fallbackAlias = null;
                for (java.util.Map.Entry<String, org.bukkit.command.Command> entry : knownCommands.entrySet()) {
                    String key = entry.getKey();
                    org.bukkit.command.Command cmd = entry.getValue();
                    if (cmd != clearlagCmd) {
                        if (key.contains(":") && key.endsWith(":clearlag")) {
                            fallbackCmd = cmd;
                        }
                        if (key.contains(":") && key.endsWith(":clearentities")) {
                            fallbackAlias = cmd;
                        }
                    }
                }

                // Remove ESCore's entries
                knownCommands.remove("clearlag");
                knownCommands.remove("clearentities");
                knownCommands.remove(pluginPrefix + ":clearlag");
                knownCommands.remove(pluginPrefix + ":clearentities");

                // If another plugin has /clearlag registered (e.g. clearlag:clearlag), restore primary mapping
                if (fallbackCmd != null) {
                    knownCommands.put("clearlag", fallbackCmd);
                    getLogger().info("ClearLag disabled in config: re-routed /clearlag to external plugin (" + fallbackCmd.getName() + ").");
                }
                if (fallbackAlias != null) {
                    knownCommands.put("clearentities", fallbackAlias);
                }
            } else {
                if (clearlagCmd != null) {
                    ClearLagCommand executor = new ClearLagCommand(this);
                    clearlagCmd.setExecutor(executor);
                    clearlagCmd.setTabCompleter(executor);
                    knownCommands.put("clearlag", clearlagCmd);
                    knownCommands.put("clearentities", clearlagCmd);
                    knownCommands.put(pluginPrefix + ":clearlag", clearlagCmd);
                    knownCommands.put(pluginPrefix + ":clearentities", clearlagCmd);
                    clearlagCmd.register(commandMap);
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to update ClearLag command registration in CommandMap", e);
        }
    }

    public static ESCoins getInstance() {
        return instance;
    }

    public static ESCoinsAPI getAPI() {
        return ESCoinsAPIProvider.get();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CoinLeaderboard getLeaderboard() {
        return leaderboard;
    }

    public CoinManager getCoinManager() {
        return coinManager;
    }

    public ClearLagManager getClearLagManager() {
        return clearLagManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public fun.endcore.escoins.chat.ChatManager getChatManager() {
        return chatManager;
    }

    public fun.endcore.escoins.arena.ArenaManager getArenaManager() {
        return arenaManager;
    }

    public fun.endcore.escoins.cosmetics.CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }

    public fun.endcore.escoins.tags.TagManager getTagManager() {
        return tagManager;
    }

    public fun.endcore.escoins.update.UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public boolean isDebugCosmeticsEnabled() {
        return configManager != null && configManager.getConfig().getBoolean("debug.cosmetics", false);
    }
}
