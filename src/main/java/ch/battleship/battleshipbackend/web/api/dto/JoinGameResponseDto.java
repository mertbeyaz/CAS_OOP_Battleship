package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

import java.util.UUID;

/**
 * Response DTO returned after successfully joining a game.
 *
 * <p>This DTO provides the client with the identifiers required for all subsequent
 * game interactions.
 *
 * @param gameCode public identifier of the game
 * @param playerId unique identifier assigned to the joining player
 * @param playerName username of the joining player
 * @param status current game status after joining
 */
public record JoinGameResponseDto(
        String gameCode,
        UUID playerId,
        String playerName,
        GameStatus status
) {}
