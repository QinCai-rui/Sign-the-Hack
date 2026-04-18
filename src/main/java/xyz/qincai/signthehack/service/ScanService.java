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
import xyz.qincai.signthehack.util.SignProbeTransport;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
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

        int checksPerSign = Math.max(1, Math.min(3, configManager.appConfig().maxChecksPerSign()));
        ArrayDeque<List<CheckDefinition>> queue = batch(checks, checksPerSign);
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
        SignProbeTransport.setAllowedEditor(placement.signBlock().getLocation(), context.target.getUniqueId(), plugin);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!active.containsKey(context.target.getUniqueId())) {
                return;
            }
            SignProbeTransport.sendBlockEntityUpdate(context.target, placement.signBlock().getLocation(), plugin);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active.containsKey(context.target.getUniqueId())) {
                    return;
                }
                SignProbeTransport.sendOpenSignEditor(context.target, placement.signBlock().getLocation(), plugin);
                context.target.sendBlockChange(placement.signBlock().getLocation(), Material.AIR.createBlockData());
            }, 1L);
        });
    }

    private ProbePlacement placeProbeSign(Player target, List<CheckDefinition> batch) {
        Location location = SignProbeTransport.findAirProbeLocation(target);
        if (location == null) {
            return null;
        }

        Block signBlock = location.getBlock();
        Block supportBlock = location.clone().add(0, -1, 0).getBlock();

        BlockState originalSignState = signBlock.getState();
        Location supportLocation = supportBlock.getLocation();
        boolean barrierPlaced = supportBlock.getType().isAir();
        if (barrierPlaced) {
            supportBlock.setType(Material.BARRIER, false);
        }

        signBlock.setType(Material.OAK_SIGN, false);

        if (!(signBlock.getState() instanceof Sign sign)) {
            originalSignState.update(true, false);
            if (barrierPlaced) {
                supportBlock.setType(Material.AIR, false);
            }
            return null;
        }

        var front = sign.getSide(Side.FRONT);
        for (int i = 0; i < 3; i++) {
            front.line(i, i < batch.size() ? buildProbeComponent(batch.get(i)) : Component.empty());
        }
        front.line(3, Component.keybind("key.forward"));
        sign.update(true, false);
        return new ProbePlacement(signBlock, originalSignState, barrierPlaced, supportLocation);
    }

    private Component buildProbeComponent(CheckDefinition check) {
        return switch (check.mode()) {
            case KEYBIND -> Component.keybind(check.key());
            case METEOR, TRANSLATE -> Component.translatable(check.key(), fallbackFor(check));
        };
    }

    private String fallbackFor(CheckDefinition check) {
        return "[NO_" + check.id().toUpperCase().replace('-', '_') + "]";
    }

    public void handleSignResponse(Player player, List<String> lines) {
        ScanContext context = active.get(player.getUniqueId());
        if (context == null || context.currentPlacement == null) {
            return;
        }

        if (context.timeoutTask != null) {
            context.timeoutTask.cancel();
            context.timeoutTask = null;
        }

        for (int i = 0; i < context.currentBatch.size(); i++) {
            CheckDefinition check = context.currentBatch.get(i);
            String response = i < lines.size() ? lines.get(i) : "";
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

    public boolean isChecking(UUID playerId) {
        return active.containsKey(playerId);
    }

    private String sanitize(String value) {
        String clean = value == null ? "" : value.replaceAll("[\r\n]", " ");
        return clean.length() > 120 ? clean.substring(0, 120) : clean;
    }

    private void cleanup(ProbePlacement placement) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                placement.originalSignState.update(true, false);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to restore probe sign block: " + exception.getMessage());
            }
            if (placement.barrierPlaced && placement.supportLocation != null) {
                try {
                    placement.supportLocation.getBlock().setType(Material.AIR, false);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Failed to restore probe support block: " + exception.getMessage());
                }
            }
        });
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
            BlockState originalSignState,
            boolean barrierPlaced,
            Location supportLocation
    ) {
    }
}
