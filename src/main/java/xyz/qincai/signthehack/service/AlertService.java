package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.detection.CheckResult;
import xyz.qincai.signthehack.detection.ScanReport;
import xyz.qincai.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertService {
    private final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();
    private final MiniMessageMessenger messenger;
    private final MessageService messageService;

    public AlertService(MiniMessageMessenger messenger, MessageService messageService) {
        this.messenger = messenger;
        this.messageService = messageService;
    }

    public boolean toggle(Player player) {
        if (subscribers.contains(player.getUniqueId())) {
            subscribers.remove(player.getUniqueId());
            return false;
        }
        subscribers.add(player.getUniqueId());
        return true;
    }

    public void notifyReport(ScanReport report, CommandSender checker) {
        String header = messageService.render("alert-header", "<gold>[Sign the Hack]</gold> <gray><name> checked by <checker> reason=<reason></gray>", Map.of(
                "<name>", report.targetName(),
                "<checker>", report.checkerName(),
                "<reason>", report.reason().key()
        ));

        boolean checkerSubscribed = checker instanceof Player player && subscribers.contains(player.getUniqueId());

        for (UUID uuid : subscribers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !player.hasPermission("signthehack.alerts")) {
                continue;
            }
            messenger.send(player, header);
            for (CheckResult result : report.results()) {
                messenger.send(player, formatResultLine(result));
            }
        }

        if (!checkerSubscribed) {
            messenger.send(checker, header);
            for (CheckResult result : report.results()) {
                messenger.send(checker, formatResultLine(result));
            }
        }
    }

    private String formatResultLine(CheckResult result) {
        return messageService.render("alert-line", "<gray>- </gray><white><check></white> <aqua><status></aqua>", Map.of(
                "<check>", result.check().displayName(),
                "<status>", result.status().name()
        ));
    }
}
