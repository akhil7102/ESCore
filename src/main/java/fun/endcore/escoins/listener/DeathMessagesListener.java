package fun.endcore.escoins.listener;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Custom death messages listener styled to match server specifications.
 * Format: ☠ <victim in red> was defeated by <killer/cause in green>
 */
public class DeathMessagesListener implements Listener {
    private final ESCoins plugin;

    public DeathMessagesListener(ESCoins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("death-messages.enabled", true)) {
            return;
        }

        // Suppress default vanilla death message
        event.deathMessage(null);

        Player victim = event.getPlayer();
        String victimName = victim.getName();
        MessageManager mm = plugin.getMessageManager();

        // 1. Direct player killer
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            broadcastDeath("death-messages.player", "&7☠ &c{VICTIM} &7was defeated by &a{KILLER}",
                    "{VICTIM}", victimName,
                    "{KILLER}", killer.getName()
            );
            return;
        }

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage == null) {
            broadcastDeath("death-messages.default", "&7☠ &c{VICTIM} &7died", "{VICTIM}", victimName, "{CAUSE}", "Unknown");
            return;
        }

        // 2. Entity damager (Mobs, Projectiles, Explosives)
        if (lastDamage instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();

            // Projectile shooter check
            if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }

            if (damager instanceof Player pDamager && !pDamager.equals(victim)) {
                broadcastDeath("death-messages.player", "&7☠ &c{VICTIM} &7was defeated by &a{KILLER}",
                        "{VICTIM}", victimName,
                        "{KILLER}", pDamager.getName()
                );
                return;
            }

            if (damager instanceof Creeper || damager instanceof TNTPrimed || damager instanceof EnderCrystal || damager instanceof org.bukkit.entity.minecart.ExplosiveMinecart) {
                broadcastDeath("death-messages.explosion", "&7☠ &c{VICTIM} &7was defeated by &aan Explosion",
                        "{VICTIM}", victimName
                );
                return;
            }

            if (damager != null) {
                String mobName = damager.getCustomName() != null ? damager.getCustomName() : formatEntityName(damager.getType().name());
                broadcastDeath("death-messages.mob", "&7☠ &c{VICTIM} &7was defeated by &a{KILLER}",
                        "{VICTIM}", victimName,
                        "{KILLER}", mobName
                );
                return;
            }
        }

        // 3. Environment & Damage Causes
        EntityDamageEvent.DamageCause cause = lastDamage.getCause();
        switch (cause) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> broadcastDeath("death-messages.explosion", "&7☠ &c{VICTIM} &7was defeated by &aan Explosion", "{VICTIM}", victimName);
            case FALL -> broadcastDeath("death-messages.fall", "&7☠ &c{VICTIM} &7hit the ground too hard", "{VICTIM}", victimName);
            case VOID -> broadcastDeath("death-messages.void", "&7☠ &c{VICTIM} &7fell into the &aVoid", "{VICTIM}", victimName);
            case LAVA -> broadcastDeath("death-messages.lava", "&7☠ &c{VICTIM} &7tried to swim in &aLava", "{VICTIM}", victimName);
            case FIRE, FIRE_TICK -> broadcastDeath("death-messages.fire", "&7☠ &c{VICTIM} &7went up in &aFlames", "{VICTIM}", victimName);
            case DROWNING -> broadcastDeath("death-messages.drowning", "&7☠ &c{VICTIM} &7drowned", "{VICTIM}", victimName);
            case MAGIC, POISON -> broadcastDeath("death-messages.magic", "&7☠ &c{VICTIM} &7was defeated by &aMagic", "{VICTIM}", victimName);
            case WITHER -> broadcastDeath("death-messages.wither", "&7☠ &c{VICTIM} &7withered away", "{VICTIM}", victimName);
            case STARVATION -> broadcastDeath("death-messages.starvation", "&7☠ &c{VICTIM} &7starved to death", "{VICTIM}", victimName);
            case SUFFOCATION -> broadcastDeath("death-messages.suffocation", "&7☠ &c{VICTIM} &7suffocated in a wall", "{VICTIM}", victimName);
            case LIGHTNING -> broadcastDeath("death-messages.lightning", "&7☠ &c{VICTIM} &7was struck by lightning", "{VICTIM}", victimName);
            default -> broadcastDeath("death-messages.default", "&7☠ &c{VICTIM} &7was defeated by &a{CAUSE}", "{VICTIM}", victimName, "{CAUSE}", formatEntityName(cause.name()));
        }
    }

    private void broadcastDeath(String path, String def, String... replacements) {
        String msg = plugin.getMessageManager().getRawMessage(path, def);
        msg = MessageManager.applyReplacements(msg, replacements);
        Bukkit.broadcast(plugin.getMessageManager().parse(msg));
    }

    private String formatEntityName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
