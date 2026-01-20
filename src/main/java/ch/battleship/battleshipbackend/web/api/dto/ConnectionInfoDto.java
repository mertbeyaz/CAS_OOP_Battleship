package ch.battleship.battleshipbackend.web.api.dto;

import java.time.Instant;

/**
 * DTO representing detailed information about a single player connection.
 *
 * <p>Used for debugging and inspection of the connection tracking table.
 * Includes player info, game info, connection status, and calculated age.
 *
 * @param playerName username of the connected player
 * @param gameCode code of the game the player is connected to
 * @param sessionId WebSocket session identifier
 * @param connected whether the connection is currently active
 * @param lastSeen timestamp of last connection activity
 * @param ageHours calculated age of the connection in hours
 */
public record ConnectionInfoDto(
        String playerName,
        String gameCode,
        String sessionId,
        boolean connected,
        Instant lastSeen,
        double ageHours
) {
}