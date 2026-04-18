package xyz.qincai.signthehack.detection;

import java.util.Locale;

public final class DetectionEvaluator {

    public CheckStatus evaluate(CheckDefinition check, String clientResponse) {
        if (clientResponse == null || clientResponse.isBlank()) {
            return CheckStatus.PROTECTED;
        }

        String normalized = clientResponse.toLowerCase(Locale.ROOT).trim();
        for (String raw : check.signatures()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String signature = raw.toLowerCase(Locale.ROOT);
            if (normalized.matches(signature) || normalized.contains(signature)) {
                return CheckStatus.DETECTED;
            }
        }

        return CheckStatus.NOT_DETECTED;
    }
}
