package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.exception.BusinessException;
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
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
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

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Operation(summary = "获取系统设置信息", description = "返回数据库路径等系统信息")
    @GetMapping("/info")
    public ApiResponse<Map<String, String>> getInfo() {
        Map<String, String> info = new HashMap<>();
        String dbPath = getDbFile().getAbsolutePath();
        String home = System.getProperty("user.home");
        info.put("dbFilePath", dbPath.startsWith(home) ? "~" + dbPath.substring(home.length()) : dbPath);
        return ApiResponse.ok(info);
    }

    @Operation(summary = "下载数据库备份", description = "下载当前 SQLite 数据库文件")
    @GetMapping("/backup/download")
    public void downloadBackup(HttpServletResponse response) throws IOException {
        File dbFile = getDbFile();
        if (!dbFile.exists()) {
            throw new BusinessException("数据库文件不存在: " + dbFile.getAbsolutePath());
        }

        String filename = "backup_" + LocalDate.now() + "_water_meter.db";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encodedFilename);
        response.setContentLengthLong(dbFile.length());

        try (FileInputStream fis = new FileInputStream(dbFile);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }

        log.info("Database backup downloaded: {}", filename);
    }

    @Operation(summary = "恢复数据库备份", description = "上传 SQLite 数据库文件并替换当前数据库，替换前会自动创建回滚备份")
    @PostMapping("/backup/restore")
    public ApiResponse<Map<String, String>> restoreBackup(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要恢复的数据库备份文件");
        }

        File dbFile = getDbFile();
        File dbDir = dbFile.getParentFile();
        if (dbDir != null) {
            dbDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File rollbackFile = new File(dbDir, "rollback_before_restore_" + timestamp + ".db");
        if (dbFile.exists()) {
            Files.copy(dbFile.toPath(), rollbackFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        Files.copy(file.getInputStream(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Map<String, String> result = new HashMap<>();
        result.put("dbFilePath", dbFile.getAbsolutePath());
        result.put("rollbackFilePath", rollbackFile.getAbsolutePath());
        log.warn("Database restored from uploaded backup. rollback={}", rollbackFile.getAbsolutePath());
        return ApiResponse.ok("恢复成功，请重启应用后继续使用", result);
    }

    private File getDbFile() {
        String path = datasourceUrl.replace("jdbc:sqlite:", "");
        int queryStart = path.indexOf('?');
        return new File(queryStart >= 0 ? path.substring(0, queryStart) : path);
    }
}
