package com.signthehack.service;

import com.signthehack.detection.CheckResult;
import com.signthehack.detection.ScanReport;
import com.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertService {
    private final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();
    private final MiniMessageMessenger messenger;

    public AlertService(MiniMessageMessenger messenger) {
        this.messenger = messenger;
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
        String header = "<gold>[Sign the Hack]</gold> <gray>" + report.targetName() + " checked by " + report.checkerName()
                + " reason=" + report.reason().key() + "</gray>";

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
        return "<gray>- </gray><white>" + result.check().displayName() + "</white> <aqua>" + result.status() + "</aqua>";
    }
}
