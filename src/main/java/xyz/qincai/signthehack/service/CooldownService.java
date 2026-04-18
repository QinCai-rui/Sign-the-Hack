package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.detection.ScanReason;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();

    public boolean isCoolingDown(UUID player, ScanReason reason, Duration duration) {
        Instant until = cooldowns.get(key(player, reason));
        return until != null && until.isAfter(Instant.now());
    }

    public void apply(UUID player, ScanReason reason, Duration duration) {
        cooldowns.put(key(player, reason), Instant.now().plus(duration));
    }

    private String key(UUID player, ScanReason reason) {
        return player + ":" + reason.name();
    }
}
