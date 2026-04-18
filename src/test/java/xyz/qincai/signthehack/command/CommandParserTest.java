package xyz.qincai.signthehack.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    @Test
    void parsesReload() {
        CommandIntent intent = parser.parse(new String[]{"reload"});
        assertEquals(CommandIntent.Type.RELOAD, intent.type());
    }

    @Test
    void parsesPlayerCheckAndCsv() {
        CommandIntent intent = parser.parse(new String[]{"Steve", "meteor-client,freecam"});
        assertEquals(CommandIntent.Type.CHECK, intent.type());
        assertEquals("Steve", intent.playerName());
        assertEquals(2, intent.checksCsv().size());
    }

    @Test
    void parsesTrigger() {
        CommandIntent intent = parser.parse(new String[]{"trigger", "Alex", "grim"});
        assertEquals(CommandIntent.Type.TRIGGER, intent.type());
        assertEquals("Alex", intent.playerName());
        assertEquals("grim", intent.triggerSource());
    }
}
