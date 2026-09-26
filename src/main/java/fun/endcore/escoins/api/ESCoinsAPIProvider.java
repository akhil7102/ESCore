package fun.endcore.escoins.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static provider for accessing the ESCoinsAPI instance.
 */
public final class ESCoinsAPIProvider {
    private static ESCoinsAPI instance;

    private ESCoinsAPIProvider() {}

    /**
     * Registers the API implementation. Internal use only.
     *
     * @param api the API implementation
     */
    public static void register(@NotNull ESCoinsAPI api) {
        ESCoinsAPIProvider.instance = api;
    }

    /**
     * Unregisters the API implementation on plugin disable. Internal use only.
     */
    public static void unregister() {
        ESCoinsAPIProvider.instance = null;
    }

    /**
     * Gets the current ESCoinsAPI instance.
     *
     * @return the ESCoinsAPI instance
     * @throws IllegalStateException if ESCoins is not loaded or enabled
     */
    public static @NotNull ESCoinsAPI get() {
        if (instance == null) {
            throw new IllegalStateException("ESCoins is not currently initialized or enabled.");
        }
        return instance;
    }

    /**
     * Gets the current ESCoinsAPI instance, or null if not yet initialized.
     *
     * @return the ESCoinsAPI instance or null
     */
    public static @Nullable ESCoinsAPI getNullable() {
        return instance;
    }
}
