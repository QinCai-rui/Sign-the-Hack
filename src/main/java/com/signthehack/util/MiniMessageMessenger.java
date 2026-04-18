package com.signthehack.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class MiniMessageMessenger {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public void send(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize(message));
    }

    public Component parse(String message) {
        return miniMessage.deserialize(message);
    }
}
