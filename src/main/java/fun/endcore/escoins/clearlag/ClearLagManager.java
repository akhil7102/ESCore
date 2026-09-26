package fun.endcore.escoins.clearlag;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * High-performance, lightweight entity cleanup system.
 * Operates with near-zero idle overhead: scans entities only when a cleanup is executed.
 */
public class ClearLagManager {
    private final ESCoins plugin;

    private boolean enabled = true;
    private int interval = 600; // seconds
    private int secondsRemaining = 600;
    private List<Integer> warnings = new ArrayList<>();
    private List<String> targetWorlds = new ArrayList<>();
    private List<String> excludedWorlds = new ArrayList<>();

    // Category toggles
    private boolean removeDroppedItems = true;
    private boolean removeMobs = true;
    private boolean removeAnimals = false;
    private boolean removeProjectiles = true;
    private boolean removeXpOrbs = true;
    private boolean removeVehicles = false;
    private boolean removeArmorStands = false;

    // Protection toggles
    private boolean protectNamed = true;
    private boolean protectPersistent = true;

    // ActionBar & Titles
    private boolean actionbarEnabled = false;
    private String actionbarContent = "{PREFIX} &f» &fEntity removal in &e{0}&f!";

    private boolean titlesEnabled = true;
    private String titleContent = "";
    private String subtitleContent = "{PREFIX} &fRemoval in &e{0}&f!";

    // Sound effects
    private boolean soundEnabled = true;
    private Sound warningSound = Sound.BLOCK_NOTE_BLOCK_PLING;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;
    private boolean countdownPitchIncrease = true;
    private Sound completeSound = Sound.BLOCK_NOTE_BLOCK_PLING;
    private float completeSoundPitch = 2.0f;

    private BukkitTask timerTask;
    private CleanupResult lastResult = CleanupResult.empty();

    public ClearLagManager(ESCoins plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        this.enabled = config.getBoolean("clearlag.enabled", true);
        this.interval = Math.max(10, config.getInt("clearlag.interval", 600));
        this.secondsRemaining = this.interval;

        this.warnings = config.getIntegerList("clearlag.warnings");
        if (this.warnings.isEmpty()) {
            this.warnings = List.of(30, 15, 10, 5, 4, 3, 2, 1);
        }

        this.targetWorlds = config.getStringList("clearlag.worlds");
        this.excludedWorlds = config.getStringList("clearlag.excluded-worlds");

        this.removeDroppedItems = config.getBoolean("clearlag.entities.dropped-items", true);
        this.removeMobs = config.getBoolean("clearlag.entities.mobs", true);
        this.removeAnimals = config.getBoolean("clearlag.entities.animals", false);
        this.removeProjectiles = config.getBoolean("clearlag.entities.projectiles", true);
        this.removeXpOrbs = config.getBoolean("clearlag.entities.xp-orbs", true);
        this.removeVehicles = config.getBoolean("clearlag.entities.vehicles", false);
        this.removeArmorStands = config.getBoolean("clearlag.entities.armor-stands", false);

        this.protectNamed = config.getBoolean("clearlag.protection.named-entities", true);
        this.protectPersistent = config.getBoolean("clearlag.protection.persistent-entities", true);

        this.actionbarEnabled = config.getBoolean("clearlag.actionbar.enabled", false);
        this.actionbarContent = config.getString("clearlag.actionbar.content", "{PREFIX} &f» &fEntity removal in &e{0}&f!");

        this.titlesEnabled = config.getBoolean("clearlag.titles.enabled", true);
        this.titleContent = config.getString("clearlag.titles.titleContent", "");
        this.subtitleContent = config.getString("clearlag.titles.subtitleContent", "{PREFIX} &fRemoval in &e{0}&f!");

        this.soundEnabled = config.getBoolean("clearlag.sound.enabled", true);
        String soundName = config.getString("clearlag.sound.name", "BLOCK_NOTE_BLOCK_PLING");
        try {
            this.warningSound = Sound.valueOf(soundName.toUpperCase());
        } catch (Exception e) {
            this.warningSound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        this.soundVolume = (float) config.getDouble("clearlag.sound.volume", 1.0);
        this.soundPitch = (float) config.getDouble("clearlag.sound.pitch", 1.0);
        this.countdownPitchIncrease = config.getBoolean("clearlag.sound.countdown-pitch-increase", true);

        String compSoundName = config.getString("clearlag.sound.complete-sound", "BLOCK_NOTE_BLOCK_PLING");
        if (compSoundName == null || compSoundName.isEmpty() || compSoundName.equalsIgnoreCase("none")) {
            this.completeSound = null;
        } else {
            try {
                this.completeSound = Sound.valueOf(compSoundName.toUpperCase());
            } catch (Exception e) {
                this.completeSound = Sound.BLOCK_NOTE_BLOCK_PLING;
            }
        }
        this.completeSoundPitch = (float) config.getDouble("clearlag.sound.complete-pitch", 2.0);

        restartTask();
    }

    public void restartTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        if (enabled) {
            // Run on primary thread every 20 ticks (1 second) to tick countdown
            timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 20L, 20L);
        }
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void onTick() {
        if (!enabled) return;

        secondsRemaining--;

        // Check for countdown warnings
        if (warnings.contains(secondsRemaining)) {
            sendWarning(secondsRemaining);
        }

        if (secondsRemaining <= 0) {
            runCleanup(null);
            secondsRemaining = interval;
        }
    }

    private void sendWarning(int seconds) {
        MessageManager mm = plugin.getMessageManager();
        String timeStr = String.valueOf(seconds);
        String clPrefix = mm.getRawMessage("clearlag.prefix", "&#FF3366&lCLEANER &8▶ ");

        // Chat broadcast
        mm.broadcastMessage("clearlag.warning", "{CLEARLAG_PREFIX}&fDropped entities are clearing in &c{TIME}s.",
                "{CLEARLAG_PREFIX}", clPrefix,
                "{TIME}", timeStr,
                "{0}", timeStr
        );

        // ActionBar
        if (actionbarEnabled && actionbarContent != null && !actionbarContent.isEmpty()) {
            mm.broadcastActionBar(actionbarContent,
                    "{CLEARLAG_PREFIX}", clPrefix,
                    "{TIME}", timeStr,
                    "{0}", timeStr
            );
        }

        // Title / Subtitle
        if (titlesEnabled) {
            mm.broadcastTitle(
                    titleContent,
                    subtitleContent,
                    100, 1000, 400,
                    "{CLEARLAG_PREFIX}", clPrefix,
                    "{TIME}", timeStr,
                    "{0}", timeStr
            );
        }

        // Play ting sound to players (especially on subtitle countdown)
        playWarningSound(seconds);
    }

    /**
     * Executes entity cleanup across allowed worlds.
     * Scans entities ONLY during execution to ensure near-zero idle server impact.
     */
    public CleanupResult runCleanup(CommandSender initiator) {
        long start = System.currentTimeMillis();

        int items = 0;
        int mobs = 0;
        int projectiles = 0;
        int xp = 0;
        int vehicles = 0;
        int armorStands = 0;

        List<World> worldsToScan = new ArrayList<>();
        if (targetWorlds.isEmpty()) {
            worldsToScan.addAll(Bukkit.getWorlds());
        } else {
            for (String worldName : targetWorlds) {
                World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    worldsToScan.add(w);
                }
            }
        }

        // Exclude worlds
        if (!excludedWorlds.isEmpty()) {
            worldsToScan.removeIf(w -> excludedWorlds.contains(w.getName()));
        }

        for (World world : worldsToScan) {
            for (Entity entity : world.getEntities()) {
                // Never remove players or NPCs
                if (entity instanceof Player || entity.hasMetadata("NPC")) {
                    continue;
                }

                // Check named entity protection
                if (protectNamed) {
                    if (entity.customName() != null || entity.getCustomName() != null) {
                        continue;
                    }
                }

                // Check persistent entity protection
                if (protectPersistent) {
                    if (entity.isPersistent()) {
                        continue;
                    }
                    if (entity instanceof Mob mob && !mob.getRemoveWhenFarAway()) {
                        continue;
                    }
                }

                // Check category removal rules
                if (entity instanceof Item) {
                    if (removeDroppedItems) {
                        entity.remove();
                        items++;
                    }
                } else if (entity instanceof ExperienceOrb) {
                    if (removeXpOrbs) {
                        entity.remove();
                        xp++;
                    }
                } else if (entity instanceof Projectile) {
                    if (removeProjectiles) {
                        entity.remove();
                        projectiles++;
                    }
                } else if (entity instanceof Monster || entity instanceof Enemy || entity instanceof Ghast || entity instanceof Slime) {
                    if (removeMobs) {
                        entity.remove();
                        mobs++;
                    }
                } else if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient) {
                    if (removeAnimals) {
                        entity.remove();
                        mobs++;
                    }
                } else if (entity instanceof Vehicle) {
                    if (removeVehicles) {
                        entity.remove();
                        vehicles++;
                    }
                } else if (entity instanceof ArmorStand) {
                    if (removeArmorStands) {
                        entity.remove();
                        armorStands++;
                    }
                }
            }
        }

        int total = items + mobs + projectiles + xp + vehicles + armorStands;
        long duration = System.currentTimeMillis() - start;
        this.lastResult = new CleanupResult(total, items, mobs, projectiles, xp, duration);

        // Broadcast completion message
        MessageManager mm = plugin.getMessageManager();
        String clPrefix = mm.getRawMessage("clearlag.prefix", "&#FF3366&lCLEANER &8▶ ");
        String totalStr = String.valueOf(total);
        String itemsStr = String.valueOf(items);
        String mobsStr = String.valueOf(mobs);
        String projStr = String.valueOf(projectiles);
        String xpStr = String.valueOf(xp);

        mm.broadcastMessage("clearlag.complete", "{CLEARLAG_PREFIX}&fDropped entities have been cleared &7(&c{TOTAL} &7removed).",
                "{CLEARLAG_PREFIX}", clPrefix,
                "{0}", totalStr,
                "{TOTAL}", totalStr,
                "{ITEMS}", itemsStr,
                "{MOBS}", mobsStr,
                "{PROJECTILES}", projStr,
                "{XP}", xpStr
        );

        if (initiator != null) {
            mm.sendMessage(initiator, "clearlag.cleared-manual", "{CLEARLAG_PREFIX}&aManual cleanup complete: removed &e{TOTAL} &aentities.",
                    "{CLEARLAG_PREFIX}", clPrefix,
                    "{0}", totalStr,
                    "{TOTAL}", totalStr,
                    "{ITEMS}", itemsStr,
                    "{MOBS}", mobsStr,
                    "{PROJECTILES}", projStr,
                    "{XP}", xpStr
            );
        }

        // Play cleanup complete sound
        playCompleteSound();

        return lastResult;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public void setSecondsRemaining(int seconds) {
        this.secondsRemaining = seconds;
    }

    public int getInterval() {
        return interval;
    }

    private void playWarningSound(int seconds) {
        if (!soundEnabled || warningSound == null) {
            return;
        }

        float pitch = soundPitch;
        if (countdownPitchIncrease && seconds <= 5 && seconds >= 1) {
            pitch = Math.min(2.0f, 1.0f + (5 - seconds) * 0.25f);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!excludedWorlds.isEmpty() && excludedWorlds.contains(player.getWorld().getName())) {
                continue;
            }
            if (targetWorlds.isEmpty() || targetWorlds.contains(player.getWorld().getName())) {
                player.playSound(player.getLocation(), warningSound, soundVolume, pitch);
            }
        }
    }

    private void playCompleteSound() {
        if (!soundEnabled || completeSound == null) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!excludedWorlds.isEmpty() && excludedWorlds.contains(player.getWorld().getName())) {
                continue;
            }
            if (targetWorlds.isEmpty() || targetWorlds.contains(player.getWorld().getName())) {
                player.playSound(player.getLocation(), completeSound, soundVolume, completeSoundPitch);
            }
        }
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public Sound getWarningSound() {
        return warningSound;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public boolean isCountdownPitchIncrease() {
        return countdownPitchIncrease;
    }

    public Sound getCompleteSound() {
        return completeSound;
    }

    public float getCompleteSoundPitch() {
        return completeSoundPitch;
    }

    public CleanupResult getLastResult() {
        return lastResult;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRemoveDroppedItems() {
        return removeDroppedItems;
    }

    public boolean isRemoveMobs() {
        return removeMobs;
    }

    public boolean isRemoveAnimals() {
        return removeAnimals;
    }

    public boolean isRemoveProjectiles() {
        return removeProjectiles;
    }

    public boolean isRemoveXpOrbs() {
        return removeXpOrbs;
    }
}
