package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.config.AppConfig;
import xyz.qincai.signthehack.config.ConfigManager;
import xyz.qincai.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateCheckerService {
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final MiniMessageMessenger messenger;
    private final HttpClient httpClient;

    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private volatile State state = State.none();
    private volatile BukkitTask repeatingTask;

    public UpdateCheckerService(JavaPlugin plugin, ConfigManager configManager, MessageService messageService, MiniMessageMessenger messenger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
        this.messenger = messenger;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public void start() {
        stop();
        AppConfig.UpdateCheckerConfig cfg = configManager.appConfig().updateChecker();
        if (!cfg.enabled()) {
            state = State.none();
            return;
        }

        runCheckAsync();

        if (cfg.intervalMinutes() > 0L) {
            long intervalTicks = Math.max(20L, cfg.intervalMinutes() * 60L * 20L);
            repeatingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    this::checkNow,
                    intervalTicks,
                    intervalTicks
            );
        }
    }

    public void reload() {
        notifiedPlayers.clear();
        start();
    }

    public void stop() {
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }

    public void runCheckAsync() {
        if (!configManager.appConfig().updateChecker().enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkNow);
    }

    public void notifyIfUpdateAvailable(Player player) {
        AppConfig.UpdateCheckerConfig cfg = configManager.appConfig().updateChecker();
        if (!cfg.enabled() || !cfg.notifyOnJoin()) {
            return;
        }
        if (!canReceiveJoinNotice(player, cfg)) {
            return;
        }

        State snapshot = state;
        if (!snapshot.updateAvailable()) {
            return;
        }
        if (!notifiedPlayers.add(player.getUniqueId())) {
            return;
        }

        String link = snapshot.downloadUrl().isBlank() ? configManager.appConfig().updateChecker().apiUrl() : snapshot.downloadUrl();
        messenger.send(player, messageService.render(
                "update-available-player",
                "<gold>[Sign the Hack]</gold> <yellow>Update available: <white><current></white> -> <white><latest></white>. <gray><url></gray></yellow>",
                Map.of(
                        "<current>", snapshot.currentVersion(),
                        "<latest>", snapshot.latestVersion(),
                        "<url>", link
                )
        ));
    }

    public String statusSummary() {
        State snapshot = state;
        if (snapshot.errorMessage() != null) {
            return "error: " + snapshot.errorMessage();
        }
        if (snapshot.currentVersion().isBlank()) {
            return "pending";
        }
        if (snapshot.updateAvailable()) {
            return "update available " + snapshot.currentVersion() + " -> " + snapshot.latestVersion();
        }
        return "up-to-date " + snapshot.currentVersion();
    }

    private boolean canReceiveJoinNotice(Player player, AppConfig.UpdateCheckerConfig cfg) {
        if (cfg.notifyPermission() != null && !cfg.notifyPermission().isBlank() && player.hasPermission(cfg.notifyPermission())) {
            return true;
        }
        return cfg.notifyOpsWithoutPermission() && player.isOp();
    }

    private void checkNow() {
        AppConfig.UpdateCheckerConfig cfg = configManager.appConfig().updateChecker();
        String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.apiUrl()))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SignTheHack-UpdateChecker")
                    .timeout(Duration.ofMillis(cfg.timeoutMillis()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                state = new State(false, currentVersion, "", "", "HTTP " + response.statusCode());
                plugin.getLogger().warning("Update check failed with HTTP status " + response.statusCode());
                return;
            }

            String body = response.body();
            String latestVersion = normalizeVersion(findFirstGroup(TAG_NAME_PATTERN, body));
            String downloadUrl = findFirstGroup(HTML_URL_PATTERN, body);
            if (latestVersion.isBlank()) {
                state = new State(false, currentVersion, "", downloadUrl, "missing tag_name in response");
                plugin.getLogger().warning("Update check failed: missing tag_name in response body");
                return;
            }

            boolean updateAvailable = !currentVersion.equalsIgnoreCase(latestVersion);
            state = new State(updateAvailable, currentVersion, latestVersion, downloadUrl, null);

            if (updateAvailable) {
                plugin.getLogger().warning("New Sign the Hack version available: " + currentVersion + " -> " + latestVersion +
                        (downloadUrl.isBlank() ? "" : " (" + downloadUrl + ")"));
            } else {
                plugin.getLogger().info("Sign the Hack is up-to-date (" + currentVersion + ")");
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            state = new State(false, currentVersion, "", "", ex.getMessage());
            plugin.getLogger().warning("Update check failed: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            state = new State(false, currentVersion, "", "", ex.getMessage());
            plugin.getLogger().warning("Update check failed: invalid URL configured");
        }
    }

    private static String findFirstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private record State(
            boolean updateAvailable,
            String currentVersion,
            String latestVersion,
            String downloadUrl,
            String errorMessage
    ) {
        private static State none() {
            return new State(false, "", "", "", null);
        }
    }
}