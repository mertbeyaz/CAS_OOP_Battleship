package ch.battleship.battleshipbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the OpenAPI / Swagger documentation.
 *
 * <p>Provides basic API metadata (title, description, version) that is displayed
 * in Swagger UI and can be used by clients to discover endpoints.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates the OpenAPI definition used by Swagger UI.
     *
     * @return configured {@link OpenAPI} instance with API metadata
     */
    @Bean
    public OpenAPI battleshipOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Battleship Game API")
                        .description("Modularbeit CAS OOP 25 – Backend Battleship mit Chat – Mert Beyaz / Michael Coppola")
                        .version("v1.0.0"));
    }
}
