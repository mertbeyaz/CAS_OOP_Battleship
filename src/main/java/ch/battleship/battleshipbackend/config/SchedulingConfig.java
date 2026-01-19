package ch.battleship.battleshipbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration for task scheduling.
 *
 * <p>Provides a {@link TaskScheduler} bean for asynchronous task execution,
 * used by:
 * <ul>
 *   <li>@Scheduled methods (e.g., ConnectionCleanupService)</li>
 *   <li>Programmatic scheduling (e.g., disconnect grace period in WebSocketEventListener)</li>
 * </ul>
 */
@Configuration
public class SchedulingConfig {

    /**
     * Creates a task scheduler with a configurable thread pool.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Pool size: 5 threads (handles scheduled cleanups + disconnect grace periods)</li>
     *   <li>Thread name prefix: "battleship-scheduler-" for easier debugging</li>
     *   <li>Initialize: Automatically on bean creation</li>
     * </ul>
     *
     * @return configured task scheduler
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("battleship-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}