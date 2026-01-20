package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for periodic cleanup of old player connection records.
 *
 * <p>This service runs a scheduled task that removes {@link PlayerConnection} entries
 * from the database that have not been active for a configurable threshold period.
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code connection.cleanup.interval-ms}: How often to run cleanup (default: 1 hour)</li>
 *   <li>{@code connection.cleanup.threshold-hours}: Age threshold for deletion (default: 24 hours)</li>
 * </ul>
 *
 * <p>The cleanup helps prevent unbounded growth of the connection tracking table
 * and removes stale data from games that have long since ended.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Getter  // Generates getters for cleanupIntervalMs and cleanupThresholdHours
public class ConnectionCleanupService {

    private final PlayerConnectionRepository connectionRepository;

    /**
     * How often the cleanup task runs (in milliseconds).
     * Configurable via {@code connection.cleanup.interval-ms}.
     * Default: 3600000ms (1 hour).
     */
    @Value("${connection.cleanup.interval-ms:3600000}")
    private long cleanupIntervalMs;

    /**
     * Age threshold in hours - connections older than this will be deleted.
     * Configurable via {@code connection.cleanup.threshold-hours}.
     * Default: 24 hours.
     */
    @Value("${connection.cleanup.threshold-hours:24}")
    private int cleanupThresholdHours;

    /**
     * Scheduled task that cleans up old player connections.
     *
     * <p>This method runs automatically at the interval defined by
     * {@code connection.cleanup.interval-ms}. It finds all connections that have
     * not been active for more than {@code connection.cleanup.threshold-hours}
     * and deletes them from the database.
     *
     * <p>The cleanup is transactional to ensure data consistency. If an error
     * occurs during cleanup, all deletions in that batch are rolled back.
     *
     * <p>Logging:
     * <ul>
     *   <li>INFO level: Reports number of connections cleaned up (only if > 0)</li>
     *   <li>DEBUG level: Logs cleanup runs even with zero deletions</li>
     * </ul>
     */
    @Scheduled(fixedRateString = "${connection.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupOldConnections() {
        // Calculate threshold timestamp: current time - threshold hours
        Instant threshold = Instant.now().minusSeconds(cleanupThresholdHours * 3600L);

        log.debug("Starting connection cleanup. Removing connections older than {} (threshold: {} hours)",
                threshold, cleanupThresholdHours);

        // Find all connections in database
        List<PlayerConnection> allConnections = connectionRepository.findAll();

        // Filter connections that are older than threshold
        List<PlayerConnection> oldConnections = allConnections.stream()
                .filter(conn -> conn.getLastSeen().isBefore(threshold))
                .toList();

        // Delete old connections if any found
        if (!oldConnections.isEmpty()) {
            connectionRepository.deleteAll(oldConnections);
            log.info("Cleaned up {} old player connection(s) (older than {} hours)",
                    oldConnections.size(), cleanupThresholdHours);
        } else {
            log.debug("No old connections to clean up");
        }
    }

    /**
     * Manually triggers the cleanup process.
     *
     * <p>This method can be called outside of the scheduled execution,
     * for example by an admin endpoint for manual cleanup or in tests.
     */
    public void triggerCleanup() {
        cleanupOldConnections();
    }
}