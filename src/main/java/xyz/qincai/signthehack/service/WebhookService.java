package xyz.qincai.signthehack.service;

import xyz.qincai.signthehack.config.AppConfig;
import xyz.qincai.signthehack.detection.ScanReport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class WebhookService {
    private final Logger logger;
    private final BlockingQueue<ScanReport> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;
    private AppConfig.WebhookConfig config;

    public WebhookService(Logger logger, AppConfig.WebhookConfig config) {
        this.logger = logger;
        this.config = config;
        this.worker = new Thread(this::loop, "signthehack-webhook");
        this.worker.start();
    }

    public void updateConfig(AppConfig.WebhookConfig webhookConfig) {
        this.config = webhookConfig;
    }

    public void enqueue(ScanReport report) {
        if (config.enabled() && config.url() != null && !config.url().isBlank()) {
            queue.offer(report);
        }
    }

    private void loop() {
        while (running) {
            try {
                ScanReport report = queue.poll(500, TimeUnit.MILLISECONDS);
                if (report == null) {
                    continue;
                }
                sendWithRetry(report);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sendWithRetry(ScanReport report) {
        int tries = 0;
        while (tries <= config.maxRetries()) {
            tries++;
            try {
                post(report);
                return;
            } catch (Exception ex) {
                if (tries > config.maxRetries()) {
                    logger.warning("Webhook send failed after retries for scan " + report.scanId() + ": " + ex.getMessage());
                    return;
                }
                try {
                    Thread.sleep(config.baseBackoffMillis() * tries);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void post(ScanReport report) throws IOException {
        String results = report.results().stream()
                .map(r -> r.check().id() + "=" + r.status())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        String hacks = report.results().stream().map(r -> r.check().displayName()).reduce((a, b) -> a + ", " + b).orElse("none");

        String payload = replaceTokens(config.payloadTemplate(), Map.of(
                "&name&", report.targetName(),
                "&checker&", report.checkerName(),
                "&reason&", report.reason().key(),
                "&hacks&", hacks,
                "&results&", results
        ));

        HttpURLConnection connection = (HttpURLConnection) URI.create(config.url()).toURL().openConnection();
        connection.setConnectTimeout(config.timeoutMillis());
        connection.setReadTimeout(config.timeoutMillis());
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        if (code < 200 || code > 299) {
            throw new IOException("HTTP " + code);
        }
    }

    private String replaceTokens(String template, Map<String, String> tokens) {
        String output = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            output = output.replace(entry.getKey(), escapeJson(entry.getValue()));
        }
        return output;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
    }
}
