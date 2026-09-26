package fun.endcore.escoins.config;

import fun.endcore.escoins.ESCoins;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Manages plugin configuration files: config.yml, messages.yml, and database.yml.
 */
public class ConfigManager {
    private final ESCoins plugin;

    private File configFile;
    private FileConfiguration config;

    private File messagesFile;
    private FileConfiguration messages;

    private File databaseFile;
    private FileConfiguration database;

    public ConfigManager(ESCoins plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // 1. config.yml
        configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        matchDefaults(config, "config.yml");

        // 2. messages.yml
        messagesFile = new File(dataFolder, "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        matchDefaults(messages, "messages.yml");

        // 3. database.yml
        databaseFile = new File(dataFolder, "database.yml");
        if (!databaseFile.exists()) {
            plugin.saveResource("database.yml", false);
        }
        database = YamlConfiguration.loadConfiguration(databaseFile);
        matchDefaults(database, "database.yml");
    }

    private void matchDefaults(FileConfiguration currentConfig, String resourceName) {
        InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            currentConfig.setDefaults(defaultConfig);
        }
    }

    public void reload() {
        loadAll();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getDatabase() {
        return database;
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", e);
        }
    }
}
