package fun.endcore.escoins.api.event;

import fun.endcore.escoins.economy.TransactionType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a player's coin balance has changed.
 */
public class CoinBalanceChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String username;
    private final long oldBalance;
    private final long newBalance;
    private final TransactionType cause;

    public CoinBalanceChangeEvent(UUID playerUuid, String username, long oldBalance, long newBalance, TransactionType cause, boolean async) {
        super(async);
        this.playerUuid = playerUuid;
        this.username = username;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.cause = cause;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getUsername() {
        return username;
    }

    public long getOldBalance() {
        return oldBalance;
    }

    public long getNewBalance() {
        return newBalance;
    }

    public TransactionType getCause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
