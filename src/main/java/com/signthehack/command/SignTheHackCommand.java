package com.signthehack.command;

import com.signthehack.SignTheHackPlugin;
import com.signthehack.detection.ScanReason;
import com.signthehack.service.AlertService;
import com.signthehack.service.CooldownService;
import com.signthehack.service.ScanService;
import com.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class SignTheHackCommand implements CommandExecutor, TabCompleter {
    private final SignTheHackPlugin plugin;
    private final ScanService scanService;
    private final AlertService alertService;
    private final CooldownService cooldownService;
    private final MiniMessageMessenger messenger;
    private final CommandParser parser = new CommandParser();

    public SignTheHackCommand(SignTheHackPlugin plugin, ScanService scanService, AlertService alertService,
                              CooldownService cooldownService, MiniMessageMessenger messenger) {
        this.plugin = plugin;
        this.scanService = scanService;
        this.alertService = alertService;
        this.cooldownService = cooldownService;
        this.messenger = messenger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandIntent intent = parser.parse(args);
        return switch (intent.type()) {
            case RELOAD -> handleReload(sender);
            case ALERTS -> handleAlerts(sender);
            case DIAGNOSE -> handleDiagnose(sender);
            case CHECK -> handleCheck(sender, intent.playerName(), intent.checksCsv());
            case INVALID -> {
                messenger.send(sender, "<yellow>Usage: /signthehack <player> [mod1,mod2] | reload | alerts | diagnose</yellow>");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("signthehack.reload")) {
            sender.sendMessage("No permission");
            return true;
        }
        plugin.reloadAll();
        messenger.send(sender, "<green>Sign the Hack configuration reloaded.</green>");
        return true;
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!sender.hasPermission("signthehack.alerts") || !(sender instanceof Player player)) {
            sender.sendMessage("No permission or console unsupported");
            return true;
        }
        boolean enabled = alertService.toggle(player);
        messenger.send(sender, enabled ? "<green>Alerts enabled.</green>" : "<red>Alerts disabled.</red>");
        return true;
    }

    private boolean handleDiagnose(CommandSender sender) {
        messenger.send(sender, "<gray>Sign the Hack diagnostics:</gray>");
        messenger.send(sender, "<gray>- Loaded checks: </gray><white>" + plugin.getConfigManager().checks().size() + "</white>");
        messenger.send(sender, "<gray>- Locale: </gray><white>" + plugin.getConfigManager().appConfig().locale() + "</white>");
        messenger.send(sender, "<gray>- Webhook enabled: </gray><white>" + plugin.getConfigManager().appConfig().webhook().enabled() + "</white>");
        return true;
    }

    private boolean handleCheck(CommandSender sender, String targetName, List<String> checks) {
        if (!sender.hasPermission("signthehack.check")) {
            sender.sendMessage("No permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messenger.send(sender, "<red>Player not found.</red>");
            return true;
        }

        Duration cooldown = Duration.ofSeconds(plugin.getConfigManager().appConfig().cooldown().manualSeconds());
        if (cooldownService.isCoolingDown(target.getUniqueId(), ScanReason.MANUAL, cooldown)) {
            messenger.send(sender, "<yellow>Manual scan cooldown active for that player.</yellow>");
            return true;
        }

        cooldownService.apply(target.getUniqueId(), ScanReason.MANUAL, cooldown);
        scanService.startScan(sender, target, checks, ScanReason.MANUAL);
        messenger.send(sender, "<gray>Started scan for </gray><white>" + target.getName() + "</white>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            result.add("reload");
            result.add("alerts");
            result.add("diagnose");
            Bukkit.getOnlinePlayers().forEach(player -> result.add(player.getName()));
            return result;
        }
        return List.of();
    }
}
