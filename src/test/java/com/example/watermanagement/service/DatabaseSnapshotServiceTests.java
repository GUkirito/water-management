package com.example.watermanagement.service;

import com.example.watermanagement.dto.DatabaseRestoreStage;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.service.impl.DatabaseSnapshotServiceImpl;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseSnapshotServiceTests {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;

    @AfterEach
    void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void snapshotContainsCommittedWalDataAndPassesIntegrityCheck() throws Exception {
        Path database = tempDir.resolve("water_meter.db");
        createRequiredSchema(database);
        dataSource = dataSource(database);
        DatabaseSnapshotServiceImpl service = service(database);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_autocheckpoint=0");
            statement.executeUpdate("INSERT INTO households(water_meter_id) VALUES ('M001')");
        }

        assertThat(Files.exists(Path.of(database + "-wal"))).isTrue();
        Path snapshot = service.createVerifiedSnapshot("test");

        assertThat(queryLong(snapshot, "SELECT COUNT(*) FROM households")).isEqualTo(1);
        assertThat(queryString(snapshot, "PRAGMA integrity_check")).isEqualTo("ok");
    }

    @Test
    void stageRestoreRejectsCorruptDatabaseWithoutTouchingProduction() throws Exception {
        Path database = tempDir.resolve("water_meter.db");
        createRequiredSchema(database);
        byte[] original = Files.readAllBytes(database);
        dataSource = dataSource(database);
        DatabaseSnapshotServiceImpl service = service(database);

        assertThatThrownBy(() -> service.stageRestore(
                new ByteArrayInputStream("not sqlite".getBytes(StandardCharsets.UTF_8)), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SQLite");

        assertThat(Files.readAllBytes(database)).isEqualTo(original);
    }

    @Test
    void stageRestoreCreatesVerifiedRollbackAndOpaqueToken() throws Exception {
        Path database = tempDir.resolve("water_meter.db");
        createRequiredSchema(database);
        dataSource = dataSource(database);
        DatabaseSnapshotServiceImpl service = service(database);
        Path uploaded = tempDir.resolve("uploaded.db");
        createRequiredSchema(uploaded);

        DatabaseRestoreStage stage;
        try (var input = Files.newInputStream(uploaded)) {
            stage = service.stageRestore(input, true);
        }

        assertThat(stage.status()).isEqualTo("STAGED");
        assertThat(stage.desktopMode()).isTrue();
        assertThat(stage.token()).matches("[0-9a-f-]{36}");
        Path staged = service.resolveStagedDatabase(stage.token());
        assertThat(staged.normalize()).startsWith(tempDir.resolve("restore-staging").normalize());
        assertThat(queryString(staged, "PRAGMA integrity_check")).isEqualTo("ok");
        assertThat(Files.exists(tempDir.resolve("restore-staging")
                .resolve(stage.token() + ".properties"))).isTrue();
    }

    @Test
    void databaseMissingRequiredTablesIsRejected() throws Exception {
        Path database = tempDir.resolve("water_meter.db");
        createRequiredSchema(database);
        dataSource = dataSource(database);
        DatabaseSnapshotServiceImpl service = service(database);
        Path incomplete = tempDir.resolve("incomplete.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + incomplete);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE households(id INTEGER PRIMARY KEY)");
        }

        assertThatThrownBy(() -> service.validateDatabase(incomplete))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必要数据表");
    }

    private DatabaseSnapshotServiceImpl service(Path database) {
        return new DatabaseSnapshotServiceImpl(
                dataSource,
                "jdbc:sqlite:" + database.toAbsolutePath(),
                tempDir.resolve("restore-staging"));
    }

    private HikariDataSource dataSource(Path database) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + database.toAbsolutePath() + "?journal_mode=WAL&busy_timeout=5000");
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }

    private void createRequiredSchema(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE households(id INTEGER PRIMARY KEY, water_meter_id TEXT)");
            statement.execute("CREATE TABLE readings(id INTEGER PRIMARY KEY, water_meter_id TEXT)");
            statement.execute("CREATE TABLE water_bills(id INTEGER PRIMARY KEY, water_meter_id TEXT)");
        }
    }

    private long queryLong(Path database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.getLong(1);
        }
    }

    private String queryString(Path database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.getString(1);
        }
    }
}
