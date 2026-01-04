package ch.battleship.battleshipbackend.web.api.dto;

/**
 * Request DTO used to join or create a lobby.
 *
 * <p>The lobby matchmaking endpoint automatically assigns the player to an open lobby
 * or creates a new one if none is available.
 *
 * @param username display name of the player
 */
public record JoinLobbyRequest(
        String username
) {}
