package com.example.watermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class WaterManagementApplication {

    public static void main(String[] args) {
        // 数据库存储在用户目录下，确保 data 文件夹存在
        // 对应 application.yml: ${user.home}/.water-management/data/water_meter.db
        String userHome = System.getProperty("user.home");
        File dataDir = new File(userHome, ".water-management/data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        SpringApplication.run(WaterManagementApplication.class, args);
    }

}
