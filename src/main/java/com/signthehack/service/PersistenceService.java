package com.signthehack.service;

import com.signthehack.db.SqlMigrations;
import com.signthehack.detection.CheckResult;
import com.signthehack.detection.ScanReport;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class PersistenceService {
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "signthehack-sqlite"));
    private final String jdbc;

    public PersistenceService(Logger logger, Path databasePath) {
        this.logger = logger;
        this.jdbc = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    public void init() {
        executor.execute(() -> {
            try (Connection connection = DriverManager.getConnection(jdbc)) {
                new SqlMigrations().migrate(connection);
            } catch (SQLException | IOException e) {
                logger.severe("Failed to init SQLite: " + e.getMessage());
            }
        });
    }

    public void saveScan(ScanReport report, List<String> actions, String renderedReason) {
        executor.execute(() -> {
            try (Connection connection = DriverManager.getConnection(jdbc)) {
                connection.setAutoCommit(false);
                long scanPk = insertScan(connection, report);
                for (CheckResult result : report.results()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO check_results(scan_id, check_id, check_name, status, detail) VALUES (?, ?, ?, ?, ?)")) {
                        statement.setLong(1, scanPk);
                        statement.setString(2, result.check().id());
                        statement.setString(3, result.check().displayName());
                        statement.setString(4, result.status().name());
                        statement.setString(5, result.detail());
                        statement.executeUpdate();
                    }
                }

                if (!actions.isEmpty() || (renderedReason != null && !renderedReason.isBlank())) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO punishment_audit(scan_id, actions, rendered_reason, created_at) VALUES (?, ?, ?, ?)")) {
                        statement.setLong(1, scanPk);
                        statement.setString(2, String.join(" | ", actions));
                        statement.setString(3, renderedReason == null ? "" : renderedReason);
                        statement.setString(4, Instant.now().toString());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                logger.warning("Failed to persist scan " + report.scanId() + ": " + e.getMessage());
            }
        });
    }

    private long insertScan(Connection connection, ScanReport report) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scans(scan_uuid, target_uuid, target_name, checker_name, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, report.scanId().toString());
            statement.setString(2, report.targetUuid().toString());
            statement.setString(3, report.targetName());
            statement.setString(4, report.checkerName());
            statement.setString(5, report.reason().name());
            statement.setString(6, report.createdAt().toString());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert scan");
    }

    public void shutdown() {
        executor.shutdown();
    }
}
