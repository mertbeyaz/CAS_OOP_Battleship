package ch.battleship.battleshipbackend.web.api.dto;

/**
 * Request DTO used to join an existing game.
 *
 * <p>Contains only the username chosen by the player. The backend generates
 * the corresponding {@code playerId} and returns it in the join response.
 *
 * @param username display name of the player joining the game
 */
public record JoinGameRequest(
        String username
) {}
