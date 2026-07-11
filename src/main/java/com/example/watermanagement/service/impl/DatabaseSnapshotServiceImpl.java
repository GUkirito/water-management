package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.DatabaseRestoreStage;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.service.DatabaseSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

@Service
public class DatabaseSnapshotServiceImpl implements DatabaseSnapshotService {

    private static final byte[] SQLITE_HEADER = "SQLite format 3\000".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> REQUIRED_TABLES = Set.of("households", "readings", "water_bills");

    private final DataSource dataSource;
    private final Path databasePath;
    private final Path stagingRoot;

    @Autowired
    public DatabaseSnapshotServiceImpl(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String datasourceUrl) {
        this(dataSource, datasourceUrl, defaultStagingRoot(datasourceUrl));
    }

    public DatabaseSnapshotServiceImpl(DataSource dataSource, String datasourceUrl, Path stagingRoot) {
        this.dataSource = dataSource;
        this.databasePath = parseDatabasePath(datasourceUrl);
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
    }

    @Override
    public Path createVerifiedSnapshot(String purpose) {
        Path tempDirectory = databasePath.toAbsolutePath().getParent().resolve("backup-temp");
        Path snapshot = tempDirectory.resolve(safePurpose(purpose) + "-" + UUID.randomUUID() + ".db");
        try {
            Files.createDirectories(tempDirectory);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + escapeSqlitePath(snapshot) + "'");
            }
            validateDatabase(snapshot);
            return snapshot;
        } catch (Exception e) {
            deleteQuietly(snapshot);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("创建数据库一致性备份失败: " + rootMessage(e));
        }
    }

    @Override
    public void validateDatabase(Path database) {
        Path normalized = database.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized) || Files.size(normalized) < SQLITE_HEADER.length) {
                throw new BusinessException("文件不是合法的 SQLite 数据库");
            }
            byte[] header = new byte[SQLITE_HEADER.length];
            try (InputStream input = Files.newInputStream(normalized)) {
                if (input.read(header) != SQLITE_HEADER.length || !java.util.Arrays.equals(header, SQLITE_HEADER)) {
                    throw new BusinessException("文件不是合法的 SQLite 数据库");
                }
            }

            Set<String> tables = new HashSet<>();
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + normalized);
                 Statement statement = connection.createStatement()) {
                try (ResultSet integrity = statement.executeQuery("PRAGMA integrity_check")) {
                    int rows = 0;
                    while (integrity.next()) {
                        rows++;
                        if (!"ok".equalsIgnoreCase(integrity.getString(1))) {
                            throw new BusinessException("SQLite 完整性检查失败: " + integrity.getString(1));
                        }
                    }
                    if (rows != 1) {
                        throw new BusinessException("SQLite 完整性检查未返回有效结果");
                    }
                }
                try (ResultSet result = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table'")) {
                    while (result.next()) {
                        tables.add(result.getString(1));
                    }
                }
            }
            if (!tables.containsAll(REQUIRED_TABLES)) {
                Set<String> missing = new HashSet<>(REQUIRED_TABLES);
                missing.removeAll(tables);
                throw new BusinessException("数据库缺少必要数据表: " + String.join(", ", missing));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证 SQLite 数据库失败: " + rootMessage(e));
        }
    }

    @Override
    public DatabaseRestoreStage stageRestore(InputStream input, boolean desktopMode) {
        String token = UUID.randomUUID().toString();
        Path partial = stagingRoot.resolve(token + ".upload.part");
        Path staged = stagingRoot.resolve(token + ".db");
        Path rollback = stagingRoot.resolve(token + ".rollback.db");
        Path descriptor = stagingRoot.resolve(token + ".properties");
        Path snapshot = null;
        try {
            Files.createDirectories(stagingRoot);
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING);
            validateDatabase(partial);
            Files.move(partial, staged, StandardCopyOption.ATOMIC_MOVE);

            snapshot = createVerifiedSnapshot("rollback");
            Files.move(snapshot, rollback, StandardCopyOption.ATOMIC_MOVE);
            snapshot = null;

            Properties properties = new Properties();
            properties.setProperty("database", databasePath.toAbsolutePath().normalize().toString());
            properties.setProperty("staged", staged.getFileName().toString());
            properties.setProperty("rollback", rollback.getFileName().toString());
            try (var output = Files.newOutputStream(descriptor,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                properties.store(output, "Validated database restore stage");
            }

            String message = desktopMode
                    ? "备份已校验，等待桌面程序安全重启并恢复"
                    : "备份已校验并暂存；Web/JAR 模式禁止在线覆盖，请停止应用后执行外部恢复";
            return new DatabaseRestoreStage(token, "STAGED", desktopMode, message);
        } catch (Exception e) {
            deleteQuietly(partial);
            deleteQuietly(staged);
            deleteQuietly(rollback);
            deleteQuietly(descriptor);
            deleteQuietly(snapshot);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("暂存数据库恢复文件失败: " + rootMessage(e));
        }
    }

    @Override
    public Path resolveStagedDatabase(String token) {
        if (token == null || !token.matches("[0-9a-fA-F-]{36}")) {
            throw new BusinessException("无效的数据库恢复令牌");
        }
        Path descriptor = requireInsideStaging(stagingRoot.resolve(token + ".properties"));
        try (InputStream input = Files.newInputStream(descriptor)) {
            Properties properties = new Properties();
            properties.load(input);
            String stagedName = properties.getProperty("staged");
            if (stagedName == null || !Path.of(stagedName).getFileName().toString().equals(stagedName)) {
                throw new BusinessException("数据库恢复令牌内容无效");
            }
            Path staged = requireInsideStaging(stagingRoot.resolve(stagedName));
            validateDatabase(staged);
            return staged;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取数据库恢复令牌失败: " + rootMessage(e));
        }
    }

    private Path requireInsideStaging(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(stagingRoot)) {
            throw new BusinessException("数据库恢复路径超出允许目录");
        }
        return normalized;
    }

    private static Path defaultStagingRoot(String datasourceUrl) {
        return parseDatabasePath(datasourceUrl).toAbsolutePath().getParent().resolve("restore-staging");
    }

    private static Path parseDatabasePath(String datasourceUrl) {
        String path = datasourceUrl.replace("jdbc:sqlite:", "")
                .replace("${user.home}", System.getProperty("user.home"));
        int queryStart = path.indexOf('?');
        return Path.of(queryStart >= 0 ? path.substring(0, queryStart) : path)
                .toAbsolutePath().normalize();
    }

    private static String safePurpose(String purpose) {
        if (purpose == null || !purpose.matches("[A-Za-z0-9_-]+")) {
            return "snapshot";
        }
        return purpose;
    }

    private static String escapeSqlitePath(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    private static String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original backup or restore error remains the actionable failure.
        }
    }
}
