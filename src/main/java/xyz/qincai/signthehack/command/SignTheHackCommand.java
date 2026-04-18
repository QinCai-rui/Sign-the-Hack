package xyz.qincai.signthehack.command;

import xyz.qincai.signthehack.SignTheHackPlugin;
import xyz.qincai.signthehack.detection.ScanReason;
import xyz.qincai.signthehack.service.AlertService;
import xyz.qincai.signthehack.service.AnticheatIntegrationService;
import xyz.qincai.signthehack.service.CooldownService;
import xyz.qincai.signthehack.service.MessageService;
import xyz.qincai.signthehack.service.ScanService;
import xyz.qincai.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SignTheHackCommand implements CommandExecutor, TabCompleter {
    private final SignTheHackPlugin plugin;
    private final ScanService scanService;
    private final AlertService alertService;
    private final CooldownService cooldownService;
    private final MiniMessageMessenger messenger;
    private final MessageService messageService;
    private final AnticheatIntegrationService anticheatIntegrationService;
    private final CommandParser parser = new CommandParser();

    public SignTheHackCommand(SignTheHackPlugin plugin, ScanService scanService, AlertService alertService,
                              CooldownService cooldownService, MiniMessageMessenger messenger,
                              MessageService messageService,
                              AnticheatIntegrationService anticheatIntegrationService) {
        this.plugin = plugin;
        this.scanService = scanService;
        this.alertService = alertService;
        this.cooldownService = cooldownService;
        this.messenger = messenger;
        this.messageService = messageService;
        this.anticheatIntegrationService = anticheatIntegrationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandIntent intent = parser.parse(args);
        return switch (intent.type()) {
            case RELOAD -> handleReload(sender);
            case ALERTS -> handleAlerts(sender);
            case DIAGNOSE -> handleDiagnose(sender);
            case TRIGGER -> handleTrigger(sender, intent.playerName(), intent.triggerSource());
            case CHECK -> handleCheck(sender, intent.playerName(), intent.checksCsv());
            case INVALID -> {
                messenger.send(sender, messageService.get("usage", "<yellow>Usage: /signthehack <player> [mod1,mod2] | reload | alerts | diagnose</yellow>"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("signthehack.reload")) {
            messenger.send(sender, messageService.get("no-permission", "<red>No permission.</red>"));
            return true;
        }
        plugin.reloadAll();
        messenger.send(sender, messageService.get("reload-ok", "<green>Configuration reloaded.</green>"));
        return true;
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!sender.hasPermission("signthehack.alerts")) {
            messenger.send(sender, messageService.get("no-permission", "<red>No permission.</red>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            messenger.send(sender, messageService.get("alerts-console-unsupported", "<red>This command is player-only.</red>"));
            return true;
        }
        boolean enabled = alertService.toggle(player);
        messenger.send(sender, messageService.get(enabled ? "alerts-on" : "alerts-off", enabled ? "<green>Alerts enabled.</green>" : "<red>Alerts disabled.</red>"));
        return true;
    }

    private boolean handleDiagnose(CommandSender sender) {
        messenger.send(sender, messageService.get("diag-header", "<gray>Sign the Hack diagnostics:</gray>"));
        plugin.diagnostics().forEach((key, value) -> messenger.send(sender, messageService.render(
                "diag-line", "<gray>- <key>: </gray><white><value></white>", Map.of("<key>", key, "<value>", value)
        )));
        messenger.send(sender, messageService.render("diag-line", "<gray>- <key>: </gray><white><value></white>", Map.of("<key>", "anticheat grim", "<value>", String.valueOf(anticheatIntegrationService.isEnabled("grim")))));
        messenger.send(sender, messageService.render("diag-line", "<gray>- <key>: </gray><white><value></white>", Map.of("<key>", "anticheat vulcan", "<value>", String.valueOf(anticheatIntegrationService.isEnabled("vulcan")))));
        messenger.send(sender, messageService.render("diag-line", "<gray>- <key>: </gray><white><value></white>", Map.of("<key>", "anticheat spartan", "<value>", String.valueOf(anticheatIntegrationService.isEnabled("spartan")))));
        return true;
    }

    private boolean handleTrigger(CommandSender sender, String targetName, String source) {
        if (!sender.hasPermission("signthehack.check")) {
            messenger.send(sender, messageService.get("no-permission", "<red>No permission.</red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messenger.send(sender, messageService.get("player-not-found", "<red>Player not found.</red>"));
            return true;
        }
        if (!anticheatIntegrationService.triggerFromCommand(target, source)) {
            messenger.send(sender, messageService.get("trigger-unknown", "<red>Unknown anticheat source.</red>"));
            return true;
        }
        messenger.send(sender, messageService.render("trigger-queued", "<gray>Trigger queued for <name></gray>", Map.of("<name>", target.getName())));
        return true;
    }

    private boolean handleCheck(CommandSender sender, String targetName, List<String> checks) {
        if (!sender.hasPermission("signthehack.check")) {
            messenger.send(sender, messageService.get("no-permission", "<red>No permission.</red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messenger.send(sender, messageService.get("player-not-found", "<red>Player not found.</red>"));
            return true;
        }

        Duration cooldown = Duration.ofSeconds(plugin.getConfigManager().appConfig().cooldown().manualSeconds());
        if (cooldownService.isCoolingDown(target.getUniqueId(), ScanReason.MANUAL, cooldown)) {
            messenger.send(sender, messageService.get("manual-cooldown", "<yellow>Manual scan cooldown active.</yellow>"));
            return true;
        }

        cooldownService.apply(target.getUniqueId(), ScanReason.MANUAL, cooldown);
        scanService.startScan(sender, target, checks, ScanReason.MANUAL);
        messenger.send(sender, messageService.render("scan-started", "<gray>Started scan for <name></gray>", Map.of("<name>", target.getName())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            result.add("reload");
            result.add("alerts");
            result.add("diagnose");
            result.add("trigger");
            Bukkit.getOnlinePlayers().forEach(player -> result.add(player.getName()));
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            List<String> result = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> result.add(player.getName()));
            return result;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trigger")) {
            return List.of("grim", "vulcan", "spartan");
        }
        return List.of();
    }
}
