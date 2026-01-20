package ch.battleship.battleshipbackend.web.api.dto;

/**
 * DTO representing the current cleanup configuration settings.
 *
 * <p>Shows the configured interval and threshold values from application.properties.
 *
 * @param cleanupIntervalMs how often cleanup runs (in milliseconds)
 * @param cleanupIntervalHours how often cleanup runs (in hours, for readability)
 * @param cleanupThresholdHours age threshold for deletion (in hours)
 */
public record CleanupConfigDto(
        long cleanupIntervalMs,
        double cleanupIntervalHours,
        int cleanupThresholdHours
) {
}