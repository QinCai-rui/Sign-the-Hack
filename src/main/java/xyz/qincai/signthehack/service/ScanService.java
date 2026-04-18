package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.config.AppConfig;
import xyz.qincai.signthehack.config.ConfigManager;
import xyz.qincai.signthehack.detection.CheckDefinition;
import xyz.qincai.signthehack.detection.CheckResult;
import xyz.qincai.signthehack.detection.CheckStatus;
import xyz.qincai.signthehack.detection.DetectionEvaluator;
import xyz.qincai.signthehack.detection.ScanReason;
import xyz.qincai.signthehack.detection.ScanReport;
import xyz.qincai.signthehack.util.MiniMessageMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ScanService {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final MiniMessageMessenger messenger;
    private final DetectionEvaluator evaluator = new DetectionEvaluator();
    private final Map<UUID, ScanContext> active = new ConcurrentHashMap<>();
    private final BiConsumer<ScanReport, CommandSender> completion;

    public ScanService(JavaPlugin plugin, ConfigManager configManager, MessageService messageService,
                       MiniMessageMessenger messenger, BiConsumer<ScanReport, CommandSender> completion) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
        this.messenger = messenger;
        this.completion = completion;
    }

    public synchronized UUID startScan(CommandSender checker, Player target, List<String> requestedChecks, ScanReason reason) {
        if (active.containsKey(target.getUniqueId())) {
            messenger.send(checker, messageService.get("probe-already-running", "<yellow>A scan is already running for this player.</yellow>"));
            return null;
        }

        List<CheckDefinition> checks = resolveChecks(reason, requestedChecks);
        if (checks.isEmpty()) {
            messenger.send(checker, messageService.get("no-checks-selected", "<yellow>No checks selected.</yellow>"));
            return null;
        }

        if (shouldSkipBedrock(target.getName())) {
            List<CheckResult> skipped = checks.stream()
                    .map(c -> new CheckResult(c, CheckStatus.SKIPPED, "Skipped by prefix rule"))
                    .toList();
            ScanReport report = new ScanReport(UUID.randomUUID(), target.getUniqueId(), target.getName(), checker.getName(), reason, Instant.now(), skipped);
            completion.accept(report, checker);
            return report.scanId();
        }

        ArrayDeque<List<CheckDefinition>> queue = batch(checks, configManager.appConfig().maxChecksPerSign());
        ScanContext context = new ScanContext(UUID.randomUUID(), checker, target, reason, queue, new ArrayList<>());
        active.put(target.getUniqueId(), context);
        runNextBatch(context);
        return context.scanId;
    }

    private boolean shouldSkipBedrock(String name) {
        AppConfig.BedrockSkipConfig bedrock = configManager.appConfig().bedrockSkip();
        if (!bedrock.enabled()) {
            return false;
        }
        return bedrock.prefixes().stream().anyMatch(name::startsWith);
    }

    private ArrayDeque<List<CheckDefinition>> batch(List<CheckDefinition> checks, int max) {
        ArrayDeque<List<CheckDefinition>> queue = new ArrayDeque<>();
        for (int i = 0; i < checks.size(); i += max) {
            queue.add(new ArrayList<>(checks.subList(i, Math.min(checks.size(), i + max))));
        }
        return queue;
    }

    private List<CheckDefinition> resolveChecks(ScanReason reason, List<String> requestedChecks) {
        if (requestedChecks != null && !requestedChecks.isEmpty()) {
            return requestedChecks.stream()
                    .map(configManager::check)
                    .filter(c -> c != null)
                    .toList();
        }
        List<String> ids = switch (reason) {
            case JOIN -> configManager.appConfig().joinChecks();
            case ANTICHEAT -> configManager.appConfig().anticheatChecks();
            case MANUAL -> configManager.appConfig().manualDefaultChecks();
        };
        return ids.stream().map(configManager::check).filter(c -> c != null).toList();
    }

    private void runNextBatch(ScanContext context) {
        if (context.queue.isEmpty()) {
            finish(context);
            return;
        }

        List<CheckDefinition> batch = context.queue.poll();
        ProbePlacement placement = placeProbeSign(context.target, batch);
        if (placement == null) {
            for (CheckDefinition check : batch) {
                context.results.add(new CheckResult(check, CheckStatus.PROTECTED, "Unable to place probe sign"));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> runNextBatch(context), configManager.appConfig().delayBetweenSignsTicks());
            return;
        }

        context.currentBatch = batch;
        context.currentPlacement = placement;
        context.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> onTimeout(context), configManager.appConfig().probeTimeoutTicks());
        context.target.openSign((Sign) placement.signBlock().getState());
    }

    private ProbePlacement placeProbeSign(Player target, List<CheckDefinition> batch) {
        Location location = target.getLocation().clone().add(0, 2, 0).getBlock().getLocation();
        Block signBlock = location.getBlock();
        Block supportBlock = location.clone().add(0, -1, 0).getBlock();

        Material originalSignType = signBlock.getType();
        var originalSignData = signBlock.getBlockData().clone();
        Material originalSupportType = supportBlock.getType();
        var originalSupportData = supportBlock.getBlockData().clone();

        supportBlock.setType(Material.BARRIER, false);
        signBlock.setType(Material.OAK_SIGN, false);

        if (!(signBlock.getState() instanceof Sign sign)) {
            signBlock.setType(originalSignType, false);
            signBlock.setBlockData(originalSignData, false);
            supportBlock.setType(originalSupportType, false);
            supportBlock.setBlockData(originalSupportData, false);
            return null;
        }

        sign.setLine(0, "[STH]");
        for (int i = 0; i < batch.size(); i++) {
            sign.setLine(i + 1, batch.get(i).key());
        }
        sign.update(true, false);
        return new ProbePlacement(signBlock, supportBlock, originalSignType, originalSignData, originalSupportType, originalSupportData);
    }

    public void handleSignResponse(Player player, Location signLocation, List<String> lines) {
        ScanContext context = active.get(player.getUniqueId());
        if (context == null || context.currentPlacement == null) {
            return;
        }
        if (!sameBlock(context.currentPlacement.signBlock().getLocation(), signLocation)) {
            return;
        }

        if (context.timeoutTask != null) {
            context.timeoutTask.cancel();
            context.timeoutTask = null;
        }

        for (int i = 0; i < context.currentBatch.size(); i++) {
            CheckDefinition check = context.currentBatch.get(i);
            String response = (i + 1) < lines.size() ? lines.get(i + 1) : "";
            CheckStatus status = evaluator.evaluate(check, response);
            context.results.add(new CheckResult(check, status, "response=" + sanitize(response)));
        }

        cleanup(context.currentPlacement);
        context.currentPlacement = null;
        context.currentBatch = null;

        Bukkit.getScheduler().runTaskLater(plugin, () -> runNextBatch(context), configManager.appConfig().delayBetweenSignsTicks());
    }

    private void onTimeout(ScanContext context) {
        if (!active.containsKey(context.target.getUniqueId())) {
            return;
        }
        if (context.currentBatch != null) {
            for (CheckDefinition check : context.currentBatch) {
                context.results.add(new CheckResult(check, CheckStatus.PROTECTED, "Timed out waiting for client sign response"));
            }
        }
        if (context.currentPlacement != null) {
            cleanup(context.currentPlacement);
            context.currentPlacement = null;
        }
        context.currentBatch = null;
        Bukkit.getScheduler().runTaskLater(plugin, () -> runNextBatch(context), configManager.appConfig().delayBetweenSignsTicks());
    }

    private void finish(ScanContext context) {
        active.remove(context.target.getUniqueId());
        ScanReport report = new ScanReport(context.scanId, context.target.getUniqueId(), context.target.getName(),
                context.checker.getName(), context.reason, Instant.now(), List.copyOf(context.results));
        completion.accept(report, context.checker);
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private String sanitize(String value) {
        String clean = value == null ? "" : value.replaceAll("[\r\n]", " ");
        return clean.length() > 120 ? clean.substring(0, 120) : clean;
    }

    private void cleanup(ProbePlacement placement) {
        placement.signBlock.setType(placement.originalSignType, false);
        placement.signBlock.setBlockData(placement.originalSignData, false);
        placement.supportBlock.setType(placement.originalSupportType, false);
        placement.supportBlock.setBlockData(placement.originalSupportData, false);
    }

    public synchronized void cancelAll() {
        for (ScanContext context : active.values()) {
            if (context.timeoutTask != null) {
                context.timeoutTask.cancel();
            }
            if (context.currentPlacement != null) {
                cleanup(context.currentPlacement);
            }
        }
        active.clear();
    }

    private static final class ScanContext {
        private final UUID scanId;
        private final CommandSender checker;
        private final Player target;
        private final ScanReason reason;
        private final ArrayDeque<List<CheckDefinition>> queue;
        private final List<CheckResult> results;
        private List<CheckDefinition> currentBatch;
        private ProbePlacement currentPlacement;
        private BukkitTask timeoutTask;

        private ScanContext(UUID scanId, CommandSender checker, Player target, ScanReason reason,
                            ArrayDeque<List<CheckDefinition>> queue, List<CheckResult> results) {
            this.scanId = scanId;
            this.checker = checker;
            this.target = target;
            this.reason = reason;
            this.queue = queue;
            this.results = results;
        }
    }

    private record ProbePlacement(
            Block signBlock,
            Block supportBlock,
            Material originalSignType,
            org.bukkit.block.data.BlockData originalSignData,
            Material originalSupportType,
            org.bukkit.block.data.BlockData originalSupportData
    ) {
    }
}
