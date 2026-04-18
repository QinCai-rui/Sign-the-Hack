package xyz.qincai.signthehack.config;

import java.util.List;
import java.util.Map;

public record AppConfig(
        boolean debug,
        String locale,
        int maxChecksPerSign,
        long delayBetweenSignsTicks,
        long probeTimeoutTicks,
        List<String> manualDefaultChecks,
        List<String> joinChecks,
        List<String> anticheatChecks,
        AutoConfig auto,
        CooldownConfig cooldown,
        BedrockSkipConfig bedrockSkip,
        ActionsConfig actions,
        WebhookConfig webhook,
        String sqliteFile
) {
    public record AutoConfig(boolean enabled, long joinDelayTicks, boolean firstJoinOnly) {
    }

    public record CooldownConfig(long manualSeconds, long joinSeconds, long anticheatSeconds) {
    }

    public record BedrockSkipConfig(boolean enabled, List<String> prefixes) {
    }

    public record ActionsConfig(
            List<String> onDetected,
            List<String> onProtected,
            List<String> onClean,
            String reasonTemplate
    ) {
    }

    public record WebhookConfig(
            boolean enabled,
            String url,
            int timeoutMillis,
            int maxRetries,
            long baseBackoffMillis,
            String payloadTemplate
    ) {
    }
}
