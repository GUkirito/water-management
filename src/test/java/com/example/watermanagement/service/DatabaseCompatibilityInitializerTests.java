package com.example.watermanagement.service;

import com.example.watermanagement.config.DatabaseCompatibilityInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCompatibilityInitializerTests {

    @TempDir
    Path tempDir;

    @Test
    void initializesNullVersionsWithoutChangingExistingVersionsAndIsIdempotent() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:sqlite:" + tempDir.resolve("water_meter.db").toAbsolutePath());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE water_bills(id INTEGER PRIMARY KEY, version INTEGER)");
        jdbcTemplate.update("INSERT INTO water_bills(id, version) VALUES (?, ?)", 1L, null);
        jdbcTemplate.update("INSERT INTO water_bills(id, version) VALUES (?, ?)", 2L, 0L);
        jdbcTemplate.update("INSERT INTO water_bills(id, version) VALUES (?, ?)", 3L, 4L);
        DatabaseCompatibilityInitializer initializer = new DatabaseCompatibilityInitializer(jdbcTemplate);

        initializer.run(null);
        initializer.run(null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM water_bills WHERE id = 1", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM water_bills WHERE id = 2", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM water_bills WHERE id = 3", Long.class)).isEqualTo(4L);
    }
}
