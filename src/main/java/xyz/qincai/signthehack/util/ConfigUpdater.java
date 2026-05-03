package xyz.qincai.signthehack.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigUpdater {

    public static void update(JavaPlugin plugin, String resourcePath, File outFile) {
        if (!outFile.exists()) {
            return;
        }
        YamlConfiguration defaultConfig = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) return;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaultConfig.load(reader);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load default " + resourcePath + ": " + e.getMessage());
            return;
        }

        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(outFile);
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!userConfig.contains(key)) {
                userConfig.set(key, defaultConfig.get(key));
                try {
                    userConfig.setComments(key, defaultConfig.getComments(key));
                    userConfig.setInlineComments(key, defaultConfig.getInlineComments(key));
                } catch (NoSuchMethodError ignored) {
                    // pre-1.18.1 Bukkit API does not support comments
                }
                changed = true;
            }
        }

        if (changed) {
            try {
                userConfig.save(outFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save updated config " + resourcePath + ": " + e.getMessage());
            }
        }
    }
}
