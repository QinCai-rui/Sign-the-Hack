package com.signthehack.command;

import java.util.List;

public record CommandIntent(Type type, String playerName, List<String> checksCsv) {
    public enum Type {
        CHECK,
        RELOAD,
        ALERTS,
        DIAGNOSE,
        INVALID
    }
}
