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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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
        String dbPath = datasourceUrl.replace("jdbc:sqlite:", "");
        Map<String, String> info = new HashMap<>();
        info.put("dbFilePath", dbPath);
        return ApiResponse.ok(info);
    }

    @Operation(summary = "下载数据库备份", description = "将当前 SQLite 数据库文件打包下载")
    @GetMapping("/backup/download")
    public void downloadBackup(HttpServletResponse response) throws IOException {
        String dbPath = datasourceUrl.replace("jdbc:sqlite:", "");
        File dbFile = new File(dbPath);

        if (!dbFile.exists()) {
            throw new BusinessException("数据库文件不存在: " + dbPath);
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

        log.info("数据库备份下载: {}", filename);
    }
}
