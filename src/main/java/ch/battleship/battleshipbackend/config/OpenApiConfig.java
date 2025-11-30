package ch.battleship.battleshipbackend.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI battleshipOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                .title("Battleship Game API")
                .description("Modularbeit CAS OOP 25 – Backend Battleship mit Chat – Mert Beyaz / Michael Coppola")
                .version("v1.0.0"));
    }
}
