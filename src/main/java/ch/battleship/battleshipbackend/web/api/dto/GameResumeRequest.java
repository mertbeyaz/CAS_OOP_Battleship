package ch.battleship.battleshipbackend.web.api.dto;

/**
 * Request body for resuming a game via a public resume token.
 *
 * @param token public resume token issued for a specific (game, player) pair
 */
public record GameResumeRequest (
    String token
) {}
