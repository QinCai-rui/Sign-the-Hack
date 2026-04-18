package xyz.qincai.signthehack.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CommandParser {

    public CommandIntent parse(String[] args) {
        if (args.length == 0) {
            return invalid();
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("reload")) {
            return new CommandIntent(CommandIntent.Type.RELOAD, null, List.of(), null);
        }
        if (first.equals("alerts")) {
            return new CommandIntent(CommandIntent.Type.ALERTS, null, List.of(), null);
        }
        if (first.equals("diagnose") || first.equals("health")) {
            return new CommandIntent(CommandIntent.Type.DIAGNOSE, null, List.of(), null);
        }
        if (first.equals("trigger") && args.length >= 3) {
            return new CommandIntent(CommandIntent.Type.TRIGGER, args[1], List.of(), args[2]);
        }

        if (args.length == 1) {
            return new CommandIntent(CommandIntent.Type.CHECK, args[0], List.of(), null);
        }

        List<String> checks = Arrays.stream(args[1].split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        return new CommandIntent(CommandIntent.Type.CHECK, args[0], checks, null);
    }

    private CommandIntent invalid() {
        return new CommandIntent(CommandIntent.Type.INVALID, null, List.of(), null);
    }
}
