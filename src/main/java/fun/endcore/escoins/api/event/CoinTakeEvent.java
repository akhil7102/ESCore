package fun.endcore.escoins.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called when coins are about to be removed from a player.
 * Can be cancelled to prevent the coin deduction.
 */
public class CoinTakeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String username;
    private long amount;
    private final String source;
    private boolean cancelled;

    public CoinTakeEvent(UUID playerUuid, String username, long amount, String source, boolean async) {
        super(async);
        this.playerUuid = playerUuid;
        this.username = username;
        this.amount = amount;
        this.source = source;
        this.cancelled = false;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getUsername() {
        return username;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        this.amount = amount;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
