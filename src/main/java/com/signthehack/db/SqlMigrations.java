package com.signthehack.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SqlMigrations {

    public void migrate(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version TEXT PRIMARY KEY)");
        }

        for (String migration : migrationNames()) {
            if (!isApplied(connection, migration)) {
                executeMigration(connection, migration);
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)")) {
                    statement.setString(1, migration);
                    statement.executeUpdate();
                }
            }
        }
    }

    private List<String> migrationNames() {
        List<String> names = new ArrayList<>();
        names.add("V1__init.sql");
        return names;
    }

    private boolean isApplied(Connection connection, String migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            statement.setString(1, migration);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void executeMigration(Connection connection, String migration) throws IOException, SQLException {
        String sql = readMigration(migration);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String readMigration(String migration) throws IOException {
        String path = "db/migrations/" + migration;
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Missing migration " + path);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }
}
