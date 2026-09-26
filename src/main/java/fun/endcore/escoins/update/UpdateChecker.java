package fun.endcore.escoins.update;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the BuiltByBit update checking background service, HTTP queries, notifications, and status.
 */
public class UpdateChecker {
    public static final String UPDATES_URL = "https://builtbybit.com/resources/core-smp.125982/updates";
    public static final String USER_AGENT = "EsCore-UpdateChecker/1.0 (Core SMP; EndCore Studios)";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ESCoins plugin;
    private final UpdateStorage storage;
    private final HttpClient httpClient;

    private boolean enabled;
    private int intervalSeconds;
    private boolean notifyConsole;
    private boolean notifyAdmins;
    private boolean firstCheckSaveBaseline;
    private int timeoutSeconds;

    private BukkitTask scheduledTask;
    private final AtomicBoolean checkInProgress = new AtomicBoolean(false);

    private long lastCheckTime = 0L;
    private long nextCheckTime = 0L;
    private UpdateInfo latestKnownUpdate = null;
    private boolean updateAvailable = false;

    // Failure throttling state
    private boolean hadFailure = false;
    private String lastFailureReason = null;

    public UpdateChecker(ESCoins plugin) {
        this.plugin = plugin;
        this.storage = new UpdateStorage(plugin.getDataFolder(), plugin.getLogger());
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        loadSettings();
    }

    private void loadSettings() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("update-checker.enabled", true);
        int rawInterval = config.getInt("update-checker.interval-seconds", 60);
        // Do not allow an interval lower than 30 seconds even if configured lower
        this.intervalSeconds = Math.max(30, rawInterval);
        this.notifyConsole = config.getBoolean("update-checker.notify-console", true);
        this.notifyAdmins = config.getBoolean("update-checker.notify-admins", true);
        this.firstCheckSaveBaseline = config.getBoolean("update-checker.first-check-save-baseline", true);
        this.timeoutSeconds = Math.max(5, config.getInt("update-checker.timeout-seconds", 10));

        this.latestKnownUpdate = storage.getLastUpdate();
    }

    /**
     * Starts the repeating update checker scheduler.
     */
    public synchronized void start() {
        stop();
        if (!enabled) {
            return;
        }

        long delayTicks = 100L; // 5 seconds initial delay after server startup
        long periodTicks = intervalSeconds * 20L;
        this.nextCheckTime = System.currentTimeMillis() + (delayTicks * 50L);

        this.scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            performScheduledCheck(null);
            this.nextCheckTime = System.currentTimeMillis() + (intervalSeconds * 1000L);
        }, delayTicks, periodTicks);
    }

    /**
     * Stops and cancels any active scheduled task.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * Reloads configuration and restarts the scheduler.
     */
    public synchronized void reloadConfig() {
        storage.load();
        loadSettings();
        start();
    }

    /**
     * Asynchronously queries the BuiltByBit updates page and parses the latest update.
     *
     * @return CompletableFuture yielding Optional of UpdateInfo if fetched and parsed
     */
    public CompletableFuture<Optional<UpdateInfo>> checkForUpdate() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(UPDATES_URL))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode != 200) {
                        handleFailure("HTTP " + statusCode + " received from BuiltByBit");
                        return Optional.<UpdateInfo>empty();
                    }

                    String html = response.body();
                    Optional<UpdateInfo> parsed = UpdateParser.parse(html, UPDATES_URL);
                    if (parsed.isEmpty()) {
                        handleFailure("Unable to parse update information from BuiltByBit page");
                        return Optional.<UpdateInfo>empty();
                    }

                    handleSuccess();
                    return parsed;
                })
                .exceptionally(ex -> {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    handleFailure(msg != null ? msg : "Connection timeout / network error");
                    return Optional.empty();
                });
    }

    /**
     * Runs the check logic: handles baseline establishment, comparison, persistence, and notifications.
     */
    private CompletableFuture<CheckResult> performScheduledCheck(CommandSender feedbackSender) {
        if (!checkInProgress.compareAndSet(false, true)) {
            if (feedbackSender != null) {
                plugin.getMessageManager().sendMessage(feedbackSender, "update.check-in-progress",
                        "{PREFIX}&7An update check is already in progress...");
            }
            return CompletableFuture.completedFuture(CheckResult.ALREADY_RUNNING);
        }

        this.lastCheckTime = System.currentTimeMillis();

        return checkForUpdate().thenApply(optUpdate -> {
            try {
                if (optUpdate.isEmpty()) {
                    if (feedbackSender != null) {
                        plugin.getMessageManager().sendMessage(feedbackSender, "update.failed",
                                "{PREFIX}&cCould not check BuiltByBit for updates: &e{REASON}",
                                "{REASON}", lastFailureReason != null ? lastFailureReason : "Unknown error");
                    }
                    return CheckResult.FAILED;
                }

                UpdateInfo fetched = optUpdate.get();
                this.latestKnownUpdate = fetched;

                // 1. Check if this is the FIRST ever check establishing the baseline
                if (!storage.hasBaseline() && firstCheckSaveBaseline) {
                    storage.save(fetched);
                    this.updateAvailable = false;
                    plugin.getLogger().info("BuiltByBit update checker initialized. Baseline set to: " + fetched.title() + " (" + fetched.id() + ")");

                    if (feedbackSender != null) {
                        plugin.getMessageManager().sendMessage(feedbackSender, "update.baseline-saved",
                                "{PREFIX}&aUpdate checker baseline established: &e{TITLE}",
                                "{TITLE}", fetched.title());
                    }
                    return CheckResult.BASELINE_SET;
                }

                // 2. Check if the update is genuinely new
                String lastId = storage.getLastUpdateId();
                if (!fetched.id().equalsIgnoreCase(lastId)) {
                    // NEW UPDATE DETECTED!
                    this.updateAvailable = true;
                    storage.save(fetched);

                    if (notifyConsole) {
                        notifyConsole(fetched);
                    }
                    if (notifyAdmins) {
                        notifyAdmins(fetched);
                    }

                    return CheckResult.NEW_UPDATE_FOUND;
                } else {
                    // Same update - do nothing
                    this.updateAvailable = false;
                    if (feedbackSender != null) {
                        plugin.getMessageManager().sendMessage(feedbackSender, "update.no-new",
                                "{PREFIX}&aNo new Core SMP updates were found.");
                    }
                    return CheckResult.NO_NEW_UPDATE;
                }
            } finally {
                checkInProgress.set(false);
            }
        });
    }

    /**
     * Forces an immediate asynchronous update check.
     */
    public void forceCheck() {
        performScheduledCheck(null);
    }

    /**
     * Forces an immediate asynchronous update check and sends results to the sender.
     */
    public void forceCheck(CommandSender sender) {
        plugin.getMessageManager().sendMessage(sender, "update.check-started",
                "{PREFIX}&7Checking BuiltByBit for updates...");
        performScheduledCheck(sender);
    }

    /**
     * Broadcasts notification to all online players with permission escore.update.notify.
     */
    public void notifyAdmins(UpdateInfo update) {
        MessageManager mm = plugin.getMessageManager();
        List<String> lines = plugin.getMessageManager().getRawList("update.chat-notify");
        if (lines == null || lines.isEmpty()) {
            lines = List.of(
                    "&8&m--------------------------------",
                    "&b&lEsCore &8» &fA new Core SMP update is available!",
                    "&fUpdate: &e{UPDATE_TITLE}",
                    "&fPublished: &7{UPDATE_DATE}",
                    "&fView: &b{UPDATE_URL}",
                    "&8&m--------------------------------"
            );
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("escore.update.notify") || player.hasPermission("escore.update.admin") || player.isOp()) {
                for (String raw : lines) {
                    String formatted = raw.replace("{UPDATE_TITLE}", update.title())
                            .replace("{UPDATE_DATE}", update.date())
                            .replace("{UPDATE_URL}", update.url());
                    player.sendMessage(mm.parse(formatted));
                }
            }
        }
    }

    /**
     * Prints update notification to console.
     */
    public void notifyConsole(UpdateInfo update) {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("New Core SMP update detected!");
        plugin.getLogger().info("Update: " + update.title());
        plugin.getLogger().info("Published: " + update.date());
        plugin.getLogger().info("URL: " + update.url());
        plugin.getLogger().info("========================================");
    }

    /**
     * Handles failure logging with throttling.
     */
    private void handleFailure(String reason) {
        if (!hadFailure || lastFailureReason == null || !lastFailureReason.equalsIgnoreCase(reason)) {
            plugin.getLogger().warning("Could not check BuiltByBit for updates: " + reason);
        }
        this.hadFailure = true;
        this.lastFailureReason = reason;
    }

    /**
     * Handles recovery logging after a failure.
     */
    private void handleSuccess() {
        if (hadFailure) {
            plugin.getLogger().info("BuiltByBit update checker connection restored.");
            hadFailure = false;
            lastFailureReason = null;
        }
    }

    /**
     * Formats status information for /escore update status.
     */
    public void sendStatus(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        String status = enabled ? "&aEnabled" : "&cDisabled";
        String lastCheck = lastCheckTime > 0 ? formatTime(lastCheckTime) : "Never";
        String nextCheck = enabled && nextCheckTime > 0 ? formatTime(nextCheckTime) : "None";

        String title = latestKnownUpdate != null ? latestKnownUpdate.title() : (storage.getLastUpdate() != null ? storage.getLastUpdate().title() : "None");
        String date = latestKnownUpdate != null ? latestKnownUpdate.date() : (storage.getLastUpdate() != null ? storage.getLastUpdate().date() : "None");

        mm.sendMessage(sender, "update.status-header", "&8&m----------------&r &b&lEsCore Update Checker &8&m----------------");
        mm.sendMessage(sender, "update.status-enabled", "&7Status: &r{STATUS}", "{STATUS}", status);
        mm.sendMessage(sender, "update.status-interval", "&7Interval: &e{INTERVAL} seconds", "{INTERVAL}", String.valueOf(intervalSeconds));
        mm.sendMessage(sender, "update.status-last-check", "&7Last Check: &e{LAST_CHECK}", "{LAST_CHECK}", lastCheck);
        mm.sendMessage(sender, "update.status-last-update", "&7Last Update: &e{TITLE}", "{TITLE}", title);
        mm.sendMessage(sender, "update.status-last-date", "&7Last Update Date: &7{DATE}", "{DATE}", date);
        mm.sendMessage(sender, "update.status-next-check", "&7Next Check: &e{NEXT_CHECK}", "{NEXT_CHECK}", nextCheck);
        mm.sendMessage(sender, "update.status-footer", "&8&m----------------------------------------------------");
    }

    private String formatTime(long epochMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs));
    }

    public UpdateInfo getLatestKnownUpdate() {
        return latestKnownUpdate != null ? latestKnownUpdate : storage.getLastUpdate();
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }

    public long getNextCheckTime() {
        return nextCheckTime;
    }

    public UpdateStorage getStorage() {
        return storage;
    }

    public enum CheckResult {
        BASELINE_SET,
        NEW_UPDATE_FOUND,
        NO_NEW_UPDATE,
        FAILED,
        ALREADY_RUNNING
    }
}
