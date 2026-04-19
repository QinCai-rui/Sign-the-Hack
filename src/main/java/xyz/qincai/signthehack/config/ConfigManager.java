package xyz.qincai.signthehack.config;

import xyz.qincai.signthehack.detection.CheckDefinition;
import xyz.qincai.signthehack.detection.DetectionMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private AppConfig appConfig;
    private Map<String, CheckDefinition> checks;
    private FileConfiguration messages;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        plugin.saveDefaultConfig();
        saveResourceIfAbsent("checks.yml");
        saveResourceIfAbsent("messages/en.yml");
        saveResourceIfAbsent("messages/it.yml");
        saveResourceIfAbsent("messages/de.yml");
        saveResourceIfAbsent("messages/es.yml");
        saveResourceIfAbsent("messages/fr.yml");
        saveResourceIfAbsent("messages/pt.yml");
        saveResourceIfAbsent("messages/ru.yml");

        plugin.reloadConfig();
        appConfig = parseAppConfig(plugin.getConfig());
        checks = parseChecks(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "checks.yml")));
        messages = loadMessages(appConfig.locale());
        validate();
    }

    private void saveResourceIfAbsent(String resourcePath) {
        File out = new File(plugin.getDataFolder(), resourcePath);
        if (!out.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private FileConfiguration loadMessages(String locale) {
        String fileName = locale.toLowerCase(Locale.ROOT) + ".yml";
        File targetFile = new File(new File(plugin.getDataFolder(), "messages"), fileName);
        if (targetFile.exists()) {
            return YamlConfiguration.loadConfiguration(targetFile);
        }

        var stream = plugin.getResource("messages/" + fileName);
        if (stream == null) {
            stream = plugin.getResource("messages/en.yml");
        }
        if (stream == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private AppConfig parseAppConfig(FileConfiguration cfg) {
        AppConfig.AutoConfig auto = new AppConfig.AutoConfig(
                cfg.getBoolean("auto.join.enabled", true),
                cfg.getLong("auto.join.delay-ticks", 60L),
                cfg.getBoolean("auto.join.first-join-only", false)
        );
        AppConfig.CooldownConfig cooldown = new AppConfig.CooldownConfig(
                cfg.getLong("cooldown.manual-seconds", 10L),
                cfg.getLong("cooldown.join-seconds", 120L),
                cfg.getLong("cooldown.anticheat-seconds", 45L)
        );
        AppConfig.BedrockSkipConfig bedrockSkip = new AppConfig.BedrockSkipConfig(
                cfg.getBoolean("bedrock-skip.enabled", true),
                cfg.getStringList("bedrock-skip.prefixes")
        );
        AppConfig.ActionsConfig actions = new AppConfig.ActionsConfig(
                cfg.getStringList("actions.on-detected"),
                cfg.getStringList("actions.on-protected"),
                cfg.getStringList("actions.on-clean"),
                cfg.getString("actions.reason-template", "<red>Sign the Hack result: <results>")
        );
        AppConfig.DetectedBroadcastConfig detectedBroadcast = new AppConfig.DetectedBroadcastConfig(
            cfg.getBoolean("detected-broadcast.enabled", false),
            cfg.getString("detected-broadcast.command", "say <name> has been temporarily banned for using <hacks>. Be good kids")
        );
        AppConfig.WebhookConfig webhook = new AppConfig.WebhookConfig(
                cfg.getBoolean("webhook.enabled", false),
                cfg.getString("webhook.url", ""),
                cfg.getInt("webhook.timeout-millis", 3000),
                cfg.getInt("webhook.max-retries", 3),
                cfg.getLong("webhook.base-backoff-millis", 500L),
                cfg.getString("webhook.payload-template", "{\"content\":\"&name& checked by &checker& (&reason&): &results&\"}")
        );

        return new AppConfig(
                cfg.getBoolean("debug", false),
                cfg.getString("locale", "en"),
                cfg.getInt("probe.max-checks-per-sign", 3),
                cfg.getLong("probe.delay-between-signs-ticks", 10L),
                cfg.getLong("probe.timeout-ticks", 60L),
                cfg.getBoolean("probe.invisible-signs", true),
                cfg.getStringList("check-sets.manual-default"),
                cfg.getStringList("check-sets.join"),
                cfg.getStringList("check-sets.anticheat"),
                auto,
                cooldown,
                bedrockSkip,
                actions,
                detectedBroadcast,
                webhook,
                cfg.getString("sqlite.file", "signthehack.db")
        );
    }

    private Map<String, CheckDefinition> parseChecks(FileConfiguration file) {
        ConfigurationSection section = file.getConfigurationSection("hacks");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, CheckDefinition> parsed = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) {
                continue;
            }
            String display = item.getString("display-name", id);
            String key = item.getString("key", "");
            DetectionMode mode = DetectionMode.valueOf(item.getString("mode", "TRANSLATE").toUpperCase(Locale.ROOT));
            String fallback = "\u27e6NO_" + id.toUpperCase(Locale.ROOT).replace('-', '_') + "\u27e7";
            List<String> signatures = new ArrayList<>(item.getStringList("signatures"));
            if (signatures.isEmpty()) {
                signatures.add(key);
                signatures.add(display);
            }
            parsed.put(id.toLowerCase(Locale.ROOT), new CheckDefinition(id, display, key, mode, fallback, signatures));
        }
        return parsed;
    }

    private void validate() {
        if (appConfig.maxChecksPerSign() < 1 || appConfig.maxChecksPerSign() > 3) {
            throw new IllegalStateException("probe.max-checks-per-sign must be between 1 and 3");
        }
        if (appConfig.probeTimeoutTicks() < 20L) {
            throw new IllegalStateException("probe.timeout-ticks must be >= 20");
        }
    }

    public AppConfig appConfig() {
        return appConfig;
    }

    public Map<String, CheckDefinition> checks() {
        return checks;
    }

    public CheckDefinition check(String id) {
        return checks.get(id.toLowerCase(Locale.ROOT));
    }

    public FileConfiguration messages() {
        return messages;
    }
}
