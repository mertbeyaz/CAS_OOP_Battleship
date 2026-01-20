package ch.battleship.battleshipbackend.web.api.dto;

/**
 * DTO representing the result of a manual cleanup operation.
 *
 * <p>Returned by the cleanup trigger endpoint to show how many
 * connections were deleted and what the database state was before/after.
 *
 * @param message human-readable status message
 * @param deletedCount number of connections that were deleted
 * @param connectionsBefore total connections before cleanup
 * @param connectionsAfter total connections after cleanup
 * @param thresholdHours the age threshold used for cleanup (in hours)
 */
public record CleanupResultDto(
        String message,
        long deletedCount,
        long connectionsBefore,
        long connectionsAfter,
        int thresholdHours
) {
}