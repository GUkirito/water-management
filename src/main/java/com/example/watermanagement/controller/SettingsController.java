package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.DatabaseRestoreStage;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.service.DatabaseSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "系统设置", description = "系统设置与数据备份")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final DatabaseSnapshotService databaseSnapshotService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${server.address:127.0.0.1}")
    private String serverAddress;

    @Operation(summary = "获取系统设置信息", description = "返回数据库路径等系统信息")
    @GetMapping("/info")
    public ApiResponse<Map<String, String>> getInfo() {
        Map<String, String> info = new HashMap<>();
        String dbPath = getDbFile().getAbsolutePath();
        String home = System.getProperty("user.home");
        info.put("dbFilePath", dbPath.startsWith(home) ? "~" + dbPath.substring(home.length()) : dbPath);
        boolean lanAccessEnabled = !("127.0.0.1".equals(serverAddress)
                || "localhost".equalsIgnoreCase(serverAddress)
                || "::1".equals(serverAddress));
        info.put("serverAddress", serverAddress);
        info.put("lanAccessEnabled", Boolean.toString(lanAccessEnabled));
        return ApiResponse.ok(info);
    }

    @Operation(summary = "下载数据库备份", description = "下载当前 SQLite 数据库文件")
    @GetMapping("/backup/download")
    public void downloadBackup(HttpServletResponse response) throws IOException {
        var snapshot = databaseSnapshotService.createVerifiedSnapshot("download");
        String filename = "backup_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_water_meter.db";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        try {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + encodedFilename);
            response.setContentLengthLong(Files.size(snapshot));

            try (FileInputStream fis = new FileInputStream(snapshot.toFile());
                 OutputStream os = response.getOutputStream()) {
                fis.transferTo(os);
                os.flush();
            }
            log.info("Database backup downloaded: {}", filename);
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    @Operation(summary = "恢复数据库备份", description = "上传 SQLite 数据库文件并替换当前数据库，替换前会自动创建回滚备份")
    @PostMapping("/backup/restore")
    public ApiResponse<DatabaseRestoreStage> restoreBackup(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "desktopMode", defaultValue = "false") boolean desktopMode) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要恢复的数据库备份文件");
        }
        DatabaseRestoreStage result = databaseSnapshotService.stageRestore(file.getInputStream(), desktopMode);
        log.warn("Database restore staged: token={}, desktopMode={}", result.token(), desktopMode);
        return ApiResponse.ok(result.message(), result);
    }

    private File getDbFile() {
        String path = datasourceUrl.replace("jdbc:sqlite:", "");
        int queryStart = path.indexOf('?');
        return new File(queryStart >= 0 ? path.substring(0, queryStart) : path);
    }
}
