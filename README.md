# ESCore — Premium Server Core Plugin

Production-ready Paper plugin built specifically for the **EndCore Lifesteal** server.
Combines a **pure premium Coins currency system**, **ultra-low overhead ClearLag**, and an **intelligent Spawn & Logout Location Restoration system**.

---

## 💎 Features

* **Platform Compatibility**: Designed for Paper (Java 25).
* **Identity**:
  * Name: `ESCore`
  * Version: `v1.0`
  * Author: `AKHILPLAYZYT`
  * JAR: `ESCore V1.0.jar`
* **Pure Premium Currency**: 
  * Independent from normal gameplay currency (Vault / EssentialsX money).
  * Does **NOT** register as a Vault economy provider, does not implement Vault Economy, and will never interfere with EssentialsX or EconomyShopGUI.
* **Classic Minecraft UI & Messages**:
  * Default Prefix: `&#33A2FF&lC&#29ACFF&lO&#1EB6FF&lR&#14BFFF&lE &#0AC9FF&lS&#00D3FF&lM&#00E5FF&lP&r `
  * Simple, clean Minecraft-style chat messages with `&` color codes, clear separators, and concise formatting.
* **Intelligent Spawn & Location Restoration System**:
  * `/setspawn`: Sets the exact world, coordinates (X, Y, Z, yaw, pitch), persisted across server restarts.
  * `/spawn`: Usable by normal players (`escoins.spawn`, no OP required). Supports configurable countdown delay, movement cancellation, and sounds.
  * **First Join**: Teleports new players directly to spawn and marks first join complete.
  * **Returning Players**: Restores player's last safe logout location when reconnecting (does not force returning players to spawn).
  * **Location Safety & Death Handling**: Validates that saved logout locations are safe (never restores into lava, void, fire, or unloaded worlds). Avoids treating death positions as safe logout locations.
* **Database Architecture**:
  * SQLite by default with **WAL mode** (`PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;`).
  * MySQL/MariaDB ready via HikariCP.
  * `long` (BIGINT) representation for coin balances (no floating-point values).
  * Atomic transactions eliminating race conditions and double spending.
* **Audit Trail & Transaction History**:
  * Logs all operations to `escoins_transactions`.
* **High-Performance Leaderboard Cache**:
  * $O(1)$ in-memory reads for PlaceholderAPI and commands.
  * If a leaderboard position is unoccupied, it returns `---`.
* **Near-Zero Idle ClearLag**:
  * Never scans entities continuously in the background; scans only when cleanup executes.
  * Configurable warnings, ActionBars, and Adventure Titles.
  * Protects players, NPCs, named mobs, and persistent entities.
* **Interactive Chat & Hover Tooltip System**:
  * Formatted chat: `{PREFIX}&f{PLAYER} &7▶ &f{MESSAGE}`.
  * Hover tooltip matching server UI with Money, BetterTeams (`%betterteams_name%`), Kills, Deaths, Playtime, Ping, and Click to Message.
  * Click-to-message suggests `/msg <player> ` directly into the chat prompt.

---

## 📜 Commands & Permissions

### Core & Admin Commands

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/escore` | View plugin version, author (`AKHILPLAYZYT`), and overview | `escoins.admin` | `op` |
| `/escore reload` | Reload all configurations, messages, and spawn settings | `escoins.reload` or `escoins.admin` | `op` |

*Aliases: `/escoins`, `/core`*

### Spawn Commands

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/spawn` | Teleport to the server spawn location | `escoins.spawn` | `true` (Non-OP) |
| `/setspawn` | Set server spawn to current position | `escoins.setspawn` | `op` |

### Coin Economy Commands

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/coins` | View own coin balance | `escoins.balance` | `true` |
| `/coins balance` | View own coin balance | `escoins.balance` | `true` |
| `/coins balance <player>` | View another player's balance | `escoins.balance.others` | `op` |
| `/coins pay <player> <amount>` | Transfer coins to another player | `escoins.pay` | `true` |
| `/coins top` | View the top coin leaderboard | `escoins.top` | `true` |
| `/coins give <player> <amount>` | Add coins to a player | `escoins.give` or `escoins.admin` | `op` |
| `/coins take <player> <amount>` | Deduct coins (never below 0) | `escoins.take` or `escoins.admin` | `op` |
| `/coins set <player> <amount>` | Set a player's exact coin balance | `escoins.set` or `escoins.admin` | `op` |
| `/coins reload` | Reload configuration and messages | `escoins.reload` or `escoins.admin` | `op` |

*Aliases: `/coin`*

### ClearLag Commands

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/clearlag` | View next cleanup timer and status | `escoins.clearlag` | `op` |
| `/clearlag clear` | Manually trigger entity removal | `escoins.clearlag` | `op` |
| `/clearlag status` | View cleanup stats and settings | `escoins.clearlag` | `op` |
| `/clearlag reload` | Reload ClearLag configuration | `escoins.clearlag` | `op` |

*Aliases: `/clearentities`*

---

## 🧩 PlaceholderAPI Placeholders

| Placeholder | Description |
| :--- | :--- |
| `%escore_coins_rank%` | Player's server-wide rank position (e.g. `#1`, `#2`, `#15`, returns `---` if unranked) |
| `%escore_coins_amount%` | Player's current coin balance (e.g. `500`) |
| `%escore_coins_amount_formatted%` | Formatted player coin balance (e.g. `500` or `1.5K`) |
| `%escore_coins_balance%` | Alias for player balance as raw integer |
| `%escore_coins_balance_formatted%` | Formatted player balance |
| `%escore_coins_top_<n>_name%` | Username at rank `<n>` (returns `---` if unoccupied) |
| `%escore_coins_top_<n>_amount%` | Coins at rank `<n>` (returns `---` if unoccupied) |
| `%escore_coins_top_<n>_ammount%` | Backwards-compatible alias for amount (`---` if unoccupied) |
| `%escore_coins_top_<n>_amount_formatted%` | Formatted coins at rank `<n>` (`---` if unoccupied) |

*Also supports `%escoins_*%` identifier (e.g. `%escoins_rank%`, `%escoins_amount%`, etc.).*

---

## 🛠 Developer API Usage

```java
import fun.endcore.escoins.api.ESCoinsAPI;
import fun.endcore.escoins.api.ESCoinsAPIProvider;

ESCoinsAPI api = ESCoinsAPIProvider.get();

// Query balance
long balance = api.getBalance(playerUuid);

// Add coins
api.giveCoins(playerUuid, 1000L, "TebexStore");

// Deduct coins
boolean success = api.takeCoins(playerUuid, 250L, "CoinShop");
```

---

## 🔨 Building

```bash
mvn clean package
```

The output JAR is generated at:
`target/ESCore V1.0.jar`
