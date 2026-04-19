package xyz.qincai.signthehack.listener;

import xyz.qincai.signthehack.config.ConfigManager;
import xyz.qincai.signthehack.detection.ScanReason;
import xyz.qincai.signthehack.service.CooldownService;
import xyz.qincai.signthehack.service.ScanService;
import xyz.qincai.signthehack.service.UpdateCheckerService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;

public final class JoinListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ScanService scanService;
    private final CooldownService cooldownService;
    private final UpdateCheckerService updateCheckerService;

    public JoinListener(JavaPlugin plugin, ConfigManager configManager, ScanService scanService, CooldownService cooldownService,
                        UpdateCheckerService updateCheckerService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scanService = scanService;
        this.cooldownService = cooldownService;
        this.updateCheckerService = updateCheckerService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateCheckerService.notifyIfUpdateAvailable(event.getPlayer());

        var cfg = configManager.appConfig();
        if (!cfg.auto().enabled()) {
            return;
        }
        if (cfg.auto().firstJoinOnly() && event.getPlayer().hasPlayedBefore()) {
            return;
        }

        Duration cd = Duration.ofSeconds(cfg.cooldown().joinSeconds());
        if (cooldownService.isCoolingDown(event.getPlayer().getUniqueId(), ScanReason.JOIN, cd)) {
            return;
        }
        cooldownService.apply(event.getPlayer().getUniqueId(), ScanReason.JOIN, cd);

        Bukkit.getScheduler().runTaskLater(plugin, () -> scanService.startScan(
                Bukkit.getConsoleSender(),
                event.getPlayer(),
                List.of(),
                ScanReason.JOIN
        ), cfg.auto().joinDelayTicks());
    }
}
