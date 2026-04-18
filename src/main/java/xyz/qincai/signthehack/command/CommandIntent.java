package xyz.qincai.signthehack.command;

import java.util.List;

public record CommandIntent(Type type, String playerName, List<String> checksCsv, String triggerSource) {
    public enum Type {
        CHECK,
        RELOAD,
        ALERTS,
        DIAGNOSE,
        TRIGGER,
        INVALID
    }
}
