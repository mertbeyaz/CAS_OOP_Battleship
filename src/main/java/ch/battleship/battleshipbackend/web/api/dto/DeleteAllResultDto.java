package ch.battleship.battleshipbackend.web.api.dto;

/**
 * DTO representing the result of deleting all connections.
 *
 * <p>Returned by the nuclear reset endpoint to confirm how many
 * connections were deleted from the database.
 *
 * @param message human-readable status message
 * @param deletedCount total number of connections that were deleted
 */
public record DeleteAllResultDto(
        String message,
        long deletedCount
) {
}