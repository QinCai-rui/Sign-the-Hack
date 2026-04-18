package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.config.ConfigManager;

import java.util.Map;

public final class MessageService {
    private final ConfigManager configManager;

    public MessageService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String get(String key, String fallback) {
        return configManager.messages().getString(key, fallback);
    }

    public String render(String key, String fallback, Map<String, String> placeholders) {
        String text = get(key, fallback);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }
}
