package com.infina.cryptopricesimulator.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI yapılandırma sınıfıdır.
 * API dokümantasyonunda gösterilecek başlık,
 * açıklama, sürüm ve iletişim bilgileri burada tanımlanır.
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI dokümantasyonunu oluşturur.
     *
     * @return OpenAPI nesnesi
     */
    @Bean
    public OpenAPI cryptoPriceSimulatorOpenAPI() {

        return new OpenAPI()

                .info(new Info()

                        .title("Crypto Price Simulator API")

                        .description("""
                                Eşzamanlı Kripto Fiyat Simülatörü

                                Bu API;
                                - Kripto fiyat simülasyonu başlatmayı
                                - Coin durumlarını görüntülemeyi
                                - Simülasyon istatistiklerini görüntülemeyi sağlar.
                                """)

                        .version("1.0.0")

                        .contact(new Contact()
                                .name("Infina Intern Team")
                                .email("team@infina.com"))

                        .license(new License()
                                .name("MIT License")))

                .externalDocs(
                        new ExternalDocumentation()
                                .description("Project Documentation")
                                .url("https://github.com/your-repository"));
    }
}