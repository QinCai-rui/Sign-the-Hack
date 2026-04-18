package xyz.qincai.signthehack.detection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScanReport(
        UUID scanId,
        UUID targetUuid,
        String targetName,
        String checkerName,
        ScanReason reason,
        Instant createdAt,
        List<CheckResult> results
) {
    public boolean hasDetected() {
        return results.stream().anyMatch(r -> r.status() == CheckStatus.DETECTED);
    }

    public boolean hasProtected() {
        return results.stream().anyMatch(r -> r.status() == CheckStatus.PROTECTED);
    }
}
