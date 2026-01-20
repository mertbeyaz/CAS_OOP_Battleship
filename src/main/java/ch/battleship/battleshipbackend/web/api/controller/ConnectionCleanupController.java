package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import ch.battleship.battleshipbackend.service.ConnectionCleanupService;
import ch.battleship.battleshipbackend.web.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Development-only REST controller for manual connection cleanup operations.
 *
 * <p>This controller is only available when the 'dev' profile is active.
 * It provides endpoints for manually triggering cleanup, inspecting connection
 * status, and managing the connection tracking table.
 *
 * <p><b>WARNING:</b> These endpoints are for development and testing purposes only.
 * They should never be exposed in production environments.
 *
 * <p>All responses use proper DTOs for type safety and documentation.
 */
@RestController
@RequestMapping("/api/dev/cleanup")
@RequiredArgsConstructor
@Profile({"dev","test","dev-reset"})  // Only available in development mode
public class ConnectionCleanupController {

    private final ConnectionCleanupService cleanupService;
    private final PlayerConnectionRepository connectionRepository;

    /**
     * Manually triggers the connection cleanup process.
     *
     * <p>This endpoint runs the same cleanup logic as the scheduled task,
     * but can be triggered on demand for testing or maintenance.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "message": "Cleanup completed",
     *   "deletedCount": 5,
     *   "connectionsBefore": 10,
     *   "connectionsAfter": 5,
     *   "thresholdHours": 24
     * }
     * </pre>
     *
     * @return cleanup result with statistics
     */
    @PostMapping("/connections")
    @Operation(summary = "Manually triggers the connection cleanup process")
    public ResponseEntity<CleanupResultDto> triggerConnectionCleanup() {
        // Get initial count
        long countBefore = connectionRepository.count();

        // Trigger cleanup
        cleanupService.triggerCleanup();

        // Get count after cleanup
        long countAfter = connectionRepository.count();
        long deletedCount = countBefore - countAfter;

        CleanupResultDto result = new CleanupResultDto(
                "Cleanup completed",
                deletedCount,
                countBefore,
                countAfter,
                cleanupService.getCleanupThresholdHours()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Gets the status of connections eligible for cleanup.
     *
     * <p>Returns statistics about how many connections are old enough
     * to be cleaned up versus how many are still recent.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "totalConnections": 10,
     *   "oldConnections": 3,
     *   "recentConnections": 7,
     *   "thresholdHours": 24,
     *   "thresholdTimestamp": "2026-01-19T20:05:39Z"
     * }
     * </pre>
     *
     * @return statistics about cleanup-eligible connections
     */
    @GetMapping("/status")
    @Operation(summary = "Gets the status of connections eligible for cleanup")
    public ResponseEntity<CleanupStatusDto> getCleanupStatus() {
        List<PlayerConnection> allConnections = connectionRepository.findAll();

        int thresholdHours = cleanupService.getCleanupThresholdHours();
        Instant threshold = Instant.now().minusSeconds(thresholdHours * 3600L);

        long oldCount = allConnections.stream()
                .filter(conn -> conn.getLastSeen().isBefore(threshold))
                .count();

        long recentCount = allConnections.size() - oldCount;

        CleanupStatusDto status = new CleanupStatusDto(
                allConnections.size(),
                oldCount,
                recentCount,
                thresholdHours,
                threshold
        );

        return ResponseEntity.ok(status);
    }

    /**
     * Gets the current cleanup configuration.
     *
     * <p>Returns the configured interval and threshold values
     * from application.properties.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "cleanupIntervalMs": 3600000,
     *   "cleanupIntervalHours": 1.0,
     *   "cleanupThresholdHours": 24
     * }
     * </pre>
     *
     * @return cleanup configuration settings
     */
    @GetMapping("/config")
    @Operation(summary = "Gets the current cleanup configuration")
    public ResponseEntity<CleanupConfigDto> getCleanupConfig() {
        long intervalMs = cleanupService.getCleanupIntervalMs();
        double intervalHours = intervalMs / 3600000.0;

        CleanupConfigDto config = new CleanupConfigDto(
                intervalMs,
                intervalHours,
                cleanupService.getCleanupThresholdHours()
        );

        return ResponseEntity.ok(config);
    }

    /**
     * Deletes ALL player connections from the database.
     *
     * <p><b>WARNING:</b> This is a destructive operation that removes
     * all connection tracking records, regardless of age. Use with caution!
     *
     * <p>Useful for:
     * <ul>
     *   <li>Resetting the database during testing</li>
     *   <li>Clearing out test data</li>
     *   <li>Starting fresh in development</li>
     * </ul>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "message": "All connections deleted",
     *   "deletedCount": 15
     * }
     * </pre>
     *
     * @return deletion result with count
     */
    @DeleteMapping("/all")
    @Operation(summary = "Deletes ALL player connections from the database")
    public ResponseEntity<DeleteAllResultDto> deleteAllConnections() {
        long countBefore = connectionRepository.count();
        connectionRepository.deleteAll();

        DeleteAllResultDto result = new DeleteAllResultDto(
                "All connections deleted",
                countBefore
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Gets a list of all current connections with details.
     *
     * <p>Returns detailed information about each connection including
     * player name, game code, connection status, and last seen timestamp.
     *
     * <p>Useful for debugging connection tracking issues.
     *
     * <p>Example response:
     * <pre>
     * [
     *   {
     *     "playerName": "Alice",
     *     "gameCode": "ABC123",
     *     "sessionId": "session-xyz",
     *     "connected": true,
     *     "lastSeen": "2026-01-20T20:05:39Z",
     *     "ageHours": 0.5
     *   }
     * ]
     * </pre>
     *
     * @return list of all connections with details
     */
    @GetMapping("/connections")
    @Operation(summary = "Gets a list of all current connections with details")
    public ResponseEntity<List<ConnectionInfoDto>> getAllConnections() {
        List<PlayerConnection> connections = connectionRepository.findAll();

        List<ConnectionInfoDto> connectionInfos = connections.stream()
                .map(this::mapToDto)
                .toList();

        return ResponseEntity.ok(connectionInfos);
    }

    /**
     * Maps a PlayerConnection entity to a ConnectionInfoDto.
     *
     * @param connection the entity to map
     * @return DTO with connection details
     */
    private ConnectionInfoDto mapToDto(PlayerConnection connection) {
        // Calculate age in hours
        long ageSeconds = Instant.now().getEpochSecond() - connection.getLastSeen().getEpochSecond();
        double ageHours = ageSeconds / 3600.0;
        double roundedAgeHours = Math.round(ageHours * 100.0) / 100.0;

        return new ConnectionInfoDto(
                connection.getPlayer().getUsername(),
                connection.getGame().getGameCode(),
                connection.getSessionId(),
                connection.isConnected(),
                connection.getLastSeen(),
                roundedAgeHours
        );
    }
}