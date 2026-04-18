package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.SignTheHackPlugin;
import xyz.qincai.signthehack.config.ConfigManager;
import xyz.qincai.signthehack.detection.CheckResult;
import xyz.qincai.signthehack.detection.ScanReport;
import xyz.qincai.signthehack.util.MiniMessageMessenger;
import xyz.qincai.signthehack.util.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ActionService {
    private final SignTheHackPlugin plugin;
    private final ConfigManager configManager;
    private final MiniMessageMessenger messenger;
    private final PlaceholderResolver placeholderResolver = new PlaceholderResolver();

    public ActionService(SignTheHackPlugin plugin, ConfigManager configManager, MiniMessageMessenger messenger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messenger = messenger;
    }

    public String execute(ScanReport report, List<CheckResult> triggering, List<String> actionCommands) {
        String checksSummary = triggering.stream()
                .map(this::formatTrigger)
                .reduce((a, b) -> a + "; " + b)
                .orElse("none");

        String reasonTemplate = configManager.appConfig().actions().reasonTemplate();
        Player target = Bukkit.getPlayer(report.targetUuid());
        String reason = placeholderResolver.apply(target, reasonTemplate, Map.of(
                "<name>", report.targetName(),
                "<checker>", report.checkerName(),
                "<reason>", report.reason().key(),
                "<results>", checksSummary,
                "<hacks>", triggering.stream().map(r -> r.check().displayName()).reduce((a, b) -> a + ", " + b).orElse("none")
        ));

        if (target != null && target.isOnline()) {
            messenger.send(target, reason);
        }

        for (String command : actionCommands) {
            String rendered = render(command, report, checksSummary);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
        }

        if (configManager.appConfig().debug()) {
            plugin.getLogger().info("scanId=" + report.scanId() + " actions=" + actionCommands.size() + " triggering=" + checksSummary);
        }
        return reason;
    }

    private String render(String template, ScanReport report, String checksSummary) {
        return template
                .replace("<name>", report.targetName())
                .replace("<checker>", report.checkerName())
                .replace("<reason>", report.reason().key())
                .replace("<results>", checksSummary)
                .replace("<hacks>", report.results().stream().map(r -> r.check().displayName()).reduce((a, b) -> a + ", " + b).orElse("none"));
    }

    private String formatTrigger(CheckResult result) {
        return result.check().id().toLowerCase(Locale.ROOT)
                + "|" + result.check().displayName()
                + "|" + result.status().name();
    }
}
