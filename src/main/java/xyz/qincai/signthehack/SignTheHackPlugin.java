package xyz.qincai.signthehack;

import xyz.qincai.signthehack.command.SignTheHackCommand;
import xyz.qincai.signthehack.config.ConfigManager;
import xyz.qincai.signthehack.detection.CheckResult;
import xyz.qincai.signthehack.detection.CheckStatus;
import xyz.qincai.signthehack.detection.ScanReport;
import xyz.qincai.signthehack.listener.JoinListener;
import xyz.qincai.signthehack.listener.SignResponseListener;
import xyz.qincai.signthehack.service.ActionService;
import xyz.qincai.signthehack.service.AlertService;
import xyz.qincai.signthehack.service.AnticheatIntegrationService;
import xyz.qincai.signthehack.service.CooldownService;
import xyz.qincai.signthehack.service.MessageService;
import xyz.qincai.signthehack.service.PersistenceService;
import xyz.qincai.signthehack.service.ScanService;
import xyz.qincai.signthehack.service.UpdateCheckerService;
import xyz.qincai.signthehack.service.WebhookService;
import xyz.qincai.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public final class SignTheHackPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private MessageService messageService;
    private MiniMessageMessenger messenger;
    private CooldownService cooldownService;
    private AlertService alertService;
    private PersistenceService persistenceService;
    private WebhookService webhookService;
    private ActionService actionService;
    private ScanService scanService;
    private AnticheatIntegrationService anticheatIntegrationService;
    private UpdateCheckerService updateCheckerService;
    private boolean tempbanAvailable;

    @Override
    public void onEnable() {
        this.messenger = new MiniMessageMessenger();
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.tempbanAvailable = hasCommand("tempban");
        if (!tempbanAvailable) {
            getLogger().warning("Command /tempban was not found. DETECTED actions will fall back to /kick.");
        }
        this.messageService = new MessageService(configManager);

        this.cooldownService = new CooldownService();
        this.alertService = new AlertService(messenger, messageService);
        this.persistenceService = new PersistenceService(getLogger(), Path.of(getDataFolder().getAbsolutePath(), configManager.appConfig().sqliteFile()));
        this.persistenceService.init();
        this.webhookService = new WebhookService(getLogger(), configManager.appConfig().webhook());
        this.actionService = new ActionService(this, configManager, messenger);
        this.scanService = new ScanService(this, configManager, messageService, messenger, this::onScanComplete);
        this.anticheatIntegrationService = new AnticheatIntegrationService(this, scanService, cooldownService, configManager);
        this.updateCheckerService = new UpdateCheckerService(this, configManager, messageService, messenger);

        var command = new SignTheHackCommand(this, scanService, alertService, cooldownService, messenger, messageService, anticheatIntegrationService);
        var pluginCommand = getCommand("signthehack");
        if (pluginCommand == null) {
            throw new IllegalStateException("Missing command registration for signthehack");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(new SignResponseListener(scanService), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, configManager, scanService, cooldownService, updateCheckerService), this);
        anticheatIntegrationService.bind();
        updateCheckerService.start();

        getLogger().info("Sign the Hack enabled.");
    }

    @Override
    public void onDisable() {
        if (scanService != null) {
            scanService.cancelAll();
        }
        if (webhookService != null) {
            webhookService.shutdown();
        }
        if (updateCheckerService != null) {
            updateCheckerService.stop();
        }
        if (persistenceService != null) {
            persistenceService.shutdown();
        }
        getLogger().info("Sign the Hack disabled.");
    }

    private void onScanComplete(ScanReport report, CommandSender checker) {
        if (configManager.appConfig().debug()) {
            getLogger().info("scanId=" + report.scanId() + " target=" + report.targetName() + " reason=" + report.reason().key());
        }

        alertService.notifyReport(report, checker);

        List<CheckResult> triggeringChecks;
        List<String> actions;
        if (report.hasDetected()) {
            triggeringChecks = report.results().stream().filter(r -> r.status() == CheckStatus.DETECTED).toList();
            actions = configManager.appConfig().actions().onDetected().stream()
                    .map(this::fallbackTempbanToKick)
                    .toList();
            runDetectedBroadcast(report, triggeringChecks);
        } else if (report.hasProtected()) {
            triggeringChecks = report.results().stream().filter(r -> r.status() == CheckStatus.PROTECTED).toList();
            actions = configManager.appConfig().actions().onProtected();
        } else {
            triggeringChecks = report.results();
            actions = configManager.appConfig().actions().onClean();
        }

        String renderedReason = actionService.execute(report, triggeringChecks, actions);
        persistenceService.saveScan(report, actions, renderedReason);
        webhookService.enqueue(report);
    }

    public void reloadAll() {
        configManager.load();
        webhookService.updateConfig(configManager.appConfig().webhook());
        updateCheckerService.reload();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Map<String, String> diagnostics() {
        return Map.of(
                "checks", String.valueOf(configManager.checks().size()),
                "locale", configManager.appConfig().locale(),
                "webhookEnabled", String.valueOf(configManager.appConfig().webhook().enabled()),
            "debug", String.valueOf(configManager.appConfig().debug()),
            "updateChecker", updateCheckerService == null ? "disabled" : updateCheckerService.statusSummary()
        );
    }

    private void runDetectedBroadcast(ScanReport report, List<CheckResult> triggeringChecks) {
        var cfg = configManager.appConfig().detectedBroadcast();
        if (!cfg.enabled()) {
            return;
        }

        String hacks = triggeringChecks.stream()
                .map(result -> result.check().displayName())
                .reduce((a, b) -> a + "," + b)
                .orElse("unknown");
        String results = triggeringChecks.stream()
                .map(result -> result.check().id().toLowerCase(Locale.ROOT) + "|" + result.check().displayName() + "|" + result.status().name())
                .reduce((a, b) -> a + "; " + b)
                .orElse("none");

        String command = cfg.command()
                .replace("<name>", report.targetName())
                .replace("<checker>", report.checkerName())
                .replace("<reason>", report.reason().key())
                .replace("<hacks>", hacks)
                .replace("<results>", results);

        if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private String fallbackTempbanToKick(String command) {
        if (tempbanAvailable || command == null) {
            return command;
        }
        String trimmed = command.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("tempban ")) {
            return command;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return trimmed;
        }

        String target = parts[1];
        StringBuilder reason = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (reason.length() > 0) {
                reason.append(' ');
            }
            reason.append(parts[i]);
        }
        if (reason.isEmpty()) {
            return "kick " + target;
        }
        return "kick " + target + " " + reason;
    }

    private boolean hasCommand(String commandName) {
        try {
            Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Method getCommand = commandMap.getClass().getMethod("getCommand", String.class);
            Object command = getCommand.invoke(commandMap, commandName);
            return command != null;
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Unable to verify command /" + commandName + ": " + ex.getMessage());
            return false;
        }
    }
}
