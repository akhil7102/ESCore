package fun.endcore.escoins.update;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages persistent storage of update checker state in plugins/EsCore/update-check.yml.
 */
public class UpdateStorage {
    private final File file;
    private final Logger logger;

    private String lastUpdateId = "";
    private UpdateInfo lastUpdate = null;

    public UpdateStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "update-check.yml");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        if (!file.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            this.lastUpdateId = config.getString("last-update-id", "");

            String title = config.getString("last-update.title", "");
            String date = config.getString("last-update.date", "");
            String url = config.getString("last-update.url", "");
            String changelogHash = config.getString("last-update.changelog-hash", "");

            if (!lastUpdateId.isBlank() || !title.isBlank()) {
                this.lastUpdate = new UpdateInfo(
                        lastUpdateId,
                        title,
                        date,
                        url,
                        "",
                        changelogHash,
                        file.lastModified()
                );
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to load update-check.yml", e);
            }
        }
    }

    public synchronized void save(UpdateInfo update) {
        if (update == null) return;

        this.lastUpdateId = update.id();
        this.lastUpdate = update;

        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("check-interval-seconds", 60);
        config.set("last-update-id", update.id());

        config.set("last-update.title", update.title());
        config.set("last-update.date", update.date());
        config.set("last-update.url", update.url());
        config.set("last-update.changelog-hash", update.changelogHash());

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to save update-check.yml", e);
            }
        }
    }

    public synchronized String getLastUpdateId() {
        return lastUpdateId != null ? lastUpdateId : "";
    }

    public synchronized UpdateInfo getLastUpdate() {
        return lastUpdate;
    }

    public synchronized boolean hasBaseline() {
        return lastUpdateId != null && !lastUpdateId.trim().isEmpty();
    }
}
