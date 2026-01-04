package ch.battleship.battleshipbackend.web.api.dto;

/**
 * DTO returned after creating a new game.
 *
 * <p>Contains the public {@code gameCode} that clients use to join or reference the game
 * in subsequent API calls.
 *
 * @param gameCode unique public identifier of the created game
 */
public record CreateGameResponseDto(
        String gameCode
) {}
