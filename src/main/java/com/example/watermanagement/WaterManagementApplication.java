package com.example.watermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
public class WaterManagementApplication {

    /** 错误日志文件路径：用户目录/.water-management/logs/startup.log */
    private static File errorLogFile;

    public static void main(String[] args) {
        // 提前准备好错误日志文件，方便排查其他电脑上的问题
        String userHome = System.getProperty("user.home");
        File baseDir = new File(userHome, ".water-management");
        File dataDir = new File(baseDir, "data");
        File logDir = new File(baseDir, "logs");
        errorLogFile = new File(logDir, "startup.log");

        try {
            // 确保目录存在
            logDir.mkdirs();
            dataDir.mkdirs();

            // 输出系统信息到日志
            logStartup("=== 村级自来水管理系统 启动日志 ===");
            logStartup("时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            logStartup("用户目录: " + userHome);
            logStartup("数据目录: " + dataDir.getAbsolutePath());
            logStartup("Java版本: " + System.getProperty("java.version"));
            logStartup("Java供应商: " + System.getProperty("java.vendor"));
            logStartup("操作系统: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            logStartup("工作目录: " + System.getProperty("user.dir"));
            logStartup("数据库路径: " + new File(dataDir, "water_meter.db").getAbsolutePath());

            SpringApplication.run(WaterManagementApplication.class, args);
            logStartup("启动成功!");

        } catch (Exception e) {
            logStartup("*** 启动失败 ***");
            logStartup("错误信息: " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logStartup("错误堆栈:\n" + sw.toString());
            // 重新抛出，让控制台也能看到
            throw e;
        }
    }

    private static void logStartup(String msg) {
        try (FileWriter fw = new FileWriter(errorLogFile, StandardCharsets.UTF_8, true)) {
            fw.write(msg + "\n");
            fw.flush();
        } catch (Exception ignored) {
            // 连日志都写不了就没救了
        }
    }
}
