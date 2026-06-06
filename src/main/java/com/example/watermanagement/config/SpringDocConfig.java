package com.example.watermanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 3 配置
 * <p>
 * 启动后访问 http://localhost:8080/swagger-ui.html 查看 API 文档页面。
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("村级自来水管理系统 API")
                        .version("1.0.0")
                        .description("提供村民管理、抄表、收费、报表等功能")
                        .contact(new Contact()
                                .name("村委会")));
    }
}
