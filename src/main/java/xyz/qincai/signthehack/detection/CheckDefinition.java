package xyz.qincai.signthehack.detection;

import java.util.List;

public record CheckDefinition(
        String id,
        String displayName,
        String key,
        DetectionMode mode,
        String fallback,
        List<String> signatures
) {
}
