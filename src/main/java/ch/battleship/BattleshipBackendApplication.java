package ch.battleship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Battleship backend.
 *
 * <p>Enables:
 * <ul>
 *   <li>Spring Boot auto-configuration</li>
 *   <li>Component scanning for the entire application</li>
 *   <li>Scheduled task execution ({@code @EnableScheduling}) for background jobs</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class BattleshipBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BattleshipBackendApplication.class, args);
    }

}