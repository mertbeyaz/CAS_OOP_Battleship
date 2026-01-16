package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service that periodically cleans up old player connections from the database.
 *
 * <p>This service runs automatically in the background to prevent the database
 * from accumulating stale connection records. Connections are considered stale
 * if they have not been active for a configurable threshold period.
 *
 * <p>Cleanup operations are scheduled to run at fixed intervals (default: every hour)
 * and remove connections that have been inactive for more than the threshold
 * duration (default: 24 hours).
 *
 * <p>Use cases for cleanup:
 * <ul>
 *   <li>Games that ended normally but connections were never explicitly removed</li>
 *   <li>Test/development connections that were never cleaned up</li>
 *   <li>Orphaned connections from server restarts or crashes</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionCleanupService {

    /**
     * Threshold in hours after which a connection is considered stale and eligible for deletion.
     * Connections with a lastSeen timestamp older than this will be removed.
     */
    private static final int CLEANUP_THRESHOLD_HOURS = 24;

    /**
     * Interval in milliseconds at which the cleanup task runs.
     * Default: 3600000 ms = 1 hour
     */
    private static final long CLEANUP_INTERVAL_MS = 3600000;

    private final PlayerConnectionRepository connectionRepository;

    /**
     * Scheduled task that cleans up old player connections.
     *
     * <p>This method runs automatically at fixed intervals (default: every hour).
     * It finds all connections that have not been active for more than
     * {@link #CLEANUP_THRESHOLD_HOURS} hours and deletes them from the database.
     *
     * <p>The cleanup is transactional to ensure data consistency. If an error
     * occurs during cleanup, all deletions in that batch are rolled back.
     *
     * <p>Logging:
     * <ul>
     *   <li>INFO level: Reports number of connections cleaned up (only if > 0)</li>
     *   <li>DEBUG level: Could be added to log cleanup runs with zero deletions</li>
     * </ul>
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    @Transactional
    public void cleanupOldConnections() {
        // Calculate threshold timestamp: current time - threshold hours
        Instant threshold = Instant.now().minusSeconds(CLEANUP_THRESHOLD_HOURS * 3600L);

        log.debug("Starting connection cleanup. Removing connections older than {}", threshold);

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
                    oldConnections.size(), CLEANUP_THRESHOLD_HOURS);
        } else {
            log.debug("No old connections to clean up");
        }
    }

    /**
     * Manually triggers a cleanup operation.
     *
     * <p>This method can be called explicitly to clean up connections outside
     * of the scheduled interval. Useful for testing or administrative operations.
     *
     * <p>Note: This is the same logic as the scheduled cleanup, just exposed
     * as a public method for manual invocation.
     */
    public void triggerCleanup() {
        log.info("Manual cleanup triggered");
        cleanupOldConnections();
    }

    /**
     * Returns the current cleanup threshold in hours.
     * Useful for monitoring or administrative interfaces.
     *
     * @return threshold in hours
     */
    public int getCleanupThresholdHours() {
        return CLEANUP_THRESHOLD_HOURS;
    }

    /**
     * Returns the cleanup interval in milliseconds.
     * Useful for monitoring or administrative interfaces.
     *
     * @return interval in milliseconds
     */
    public long getCleanupIntervalMs() {
        return CLEANUP_INTERVAL_MS;
    }
}