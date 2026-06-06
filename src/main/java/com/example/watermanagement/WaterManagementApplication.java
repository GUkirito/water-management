package com.example.watermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class WaterManagementApplication {

    public static void main(String[] args) {
        // 确保 data 目录存在，SQLite JDBC 不会自动创建父文件夹
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        SpringApplication.run(WaterManagementApplication.class, args);
    }

}
