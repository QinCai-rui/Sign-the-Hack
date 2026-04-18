package com.signthehack.detection;

import java.util.List;

public record CheckDefinition(
        String id,
        String displayName,
        String key,
        DetectionMode mode,
        List<String> signatures
) {
}
