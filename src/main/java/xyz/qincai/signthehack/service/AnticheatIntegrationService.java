package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.config.ConfigManager;
import xyz.qincai.signthehack.detection.ScanReason;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnticheatIntegrationService {
    private final JavaPlugin plugin;
    private final ScanService scanService;
    private final CooldownService cooldownService;
    private final ConfigManager configManager;
    private final Map<String, Boolean> active = new ConcurrentHashMap<>();

    public AnticheatIntegrationService(JavaPlugin plugin, ScanService scanService, CooldownService cooldownService, ConfigManager configManager) {
        this.plugin = plugin;
        this.scanService = scanService;
        this.cooldownService = cooldownService;
        this.configManager = configManager;
    }

    public void bind() {
        bindIfPresent("GrimAC", "ac.grim.grimac.api.events.GrimFlagEvent", "grim");
        bindIfPresent("Vulcan", "me.frep.vulcan.api.event.VulcanFlagEvent", "vulcan");
        bindIfPresent("Spartan", "me.vagdedes.spartan.api.PlayerViolationEvent", "spartan");
    }

    private void bindIfPresent(String pluginName, String eventClassName, String sourceKey) {
        Plugin external = Bukkit.getPluginManager().getPlugin(pluginName);
        if (external == null || !external.isEnabled()) {
            active.put(sourceKey, false);
            return;
        }

        try {
            Class<?> eventClass = Class.forName(eventClassName);
            EventExecutor executor = (listener, event) -> handleFlagEvent(sourceKey, event);
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, new Listener() { }, EventPriority.MONITOR, executor, plugin, true);
            active.put(sourceKey, true);
            plugin.getLogger().info("Hooked anticheat source " + sourceKey + " via " + eventClassName);
        } catch (ClassNotFoundException | EventException ex) {
            active.put(sourceKey, false);
            plugin.getLogger().warning("Anticheat source " + sourceKey + " detected but API class not available: " + ex.getMessage());
        }
    }

    private void handleFlagEvent(String sourceKey, Event event) {
        Player player = extractPlayer(event);
        if (player == null) {
            return;
        }
        Duration cooldown = Duration.ofSeconds(configManager.appConfig().cooldown().anticheatSeconds());
        if (cooldownService.isCoolingDown(player.getUniqueId(), ScanReason.ANTICHEAT, cooldown)) {
            return;
        }
        cooldownService.apply(player.getUniqueId(), ScanReason.ANTICHEAT, cooldown);
        scanService.startScan(Bukkit.getConsoleSender(), player, List.of(), ScanReason.ANTICHEAT);
        if (configManager.appConfig().debug()) {
            plugin.getLogger().info("scanId=queued target=" + player.getName() + " reason=anticheat source=" + sourceKey);
        }
    }

    private Player extractPlayer(Event event) {
        List<String> methodCandidates = List.of("getPlayer", "getBukkitPlayer", "getViolationPlayer");
        for (String methodName : methodCandidates) {
            try {
                var method = event.getClass().getMethod(methodName);
                Object value = method.invoke(event);
                if (value instanceof Player player) {
                    return player;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public boolean isEnabled(String source) {
        return active.getOrDefault(source.toLowerCase(Locale.ROOT), false);
    }

    public boolean triggerFromCommand(Player player, String source) {
        String normalized = source.toLowerCase(Locale.ROOT);
        if (!List.of("grim", "vulcan", "spartan").contains(normalized)) {
            return false;
        }
        Duration cooldown = Duration.ofSeconds(configManager.appConfig().cooldown().anticheatSeconds());
        if (cooldownService.isCoolingDown(player.getUniqueId(), ScanReason.ANTICHEAT, cooldown)) {
            return true;
        }
        cooldownService.apply(player.getUniqueId(), ScanReason.ANTICHEAT, cooldown);
        scanService.startScan(Bukkit.getConsoleSender(), player, List.of(), ScanReason.ANTICHEAT);
        return true;
    }
}
