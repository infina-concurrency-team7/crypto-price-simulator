package com.infina.cryptopricesimulator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kripto Fiyat Simülatörü API")
                        .version("1.0.0")
                        .description("Infina Akademi staj projesi kapsamında geliştirilmiş, " +
                                "Java Virtual Threads / Platform Threads ve multi-threading (Eşzamanlılık) " +
                                "performansını ölçen, safe ve unsafe koşan simülatör motoru.")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")));
    }
}
