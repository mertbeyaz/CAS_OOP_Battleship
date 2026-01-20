package ch.battleship.battleshipbackend.web.api.dto;

import java.time.Instant;

/**
 * DTO representing the current status of player connections eligible for cleanup.
 *
 * <p>Provides statistics about how many connections are old enough to be
 * cleaned up versus how many are still recent, based on the configured threshold.
 *
 * @param totalConnections total number of tracked connections
 * @param oldConnections number of connections older than threshold
 * @param recentConnections number of connections within threshold
 * @param thresholdHours the age threshold in hours
 * @param thresholdTimestamp the exact timestamp used as threshold
 */
public record CleanupStatusDto(
        int totalConnections,
        long oldConnections,
        long recentConnections,
        int thresholdHours,
        Instant thresholdTimestamp
) {
}