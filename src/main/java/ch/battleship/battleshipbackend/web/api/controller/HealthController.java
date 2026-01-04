package ch.battleship.battleshipbackend.web.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple health check controller.
 *
 * <p>Provides a lightweight endpoint to verify that the backend service is running
 * and reachable. This endpoint does not perform any dependency checks (e.g. database),
 * but only confirms that the application context is alive.
 *
 * <p>Typical use cases:
 * <ul>
 *   <li>Container / orchestration health checks</li>
 *   <li>Load balancer availability probes</li>
 *   <li>Quick manual verification during development</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Health check endpoint.
     *
     * @return static string {@code "OK"} if the service is up
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
