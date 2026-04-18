package xyz.qincai.signthehack.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlMigrationsTest {

    @Test
    void createsExpectedTables() throws Exception {
        Path dbPath = Files.createTempFile("signthehack-test", ".db");
        String jdbc = "jdbc:sqlite:" + dbPath;

        try (Connection connection = DriverManager.getConnection(jdbc)) {
            new SqlMigrations().migrate(connection);

            assertTrue(tableExists(connection, "scans"));
            assertTrue(tableExists(connection, "check_results"));
            assertTrue(tableExists(connection, "punishment_audit"));
            assertTrue(tableExists(connection, "schema_migrations"));
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }
}
