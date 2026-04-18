package xyz.qincai.signthehack.detection;

public record CheckResult(
        CheckDefinition check,
        CheckStatus status,
        String detail
) {
}
