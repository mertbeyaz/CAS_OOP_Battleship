package ch.battleship.battleshipbackend.web.api.dto;

import java.util.UUID;

/**
 * Request DTO used to fire a shot in a game.
 *
 * <p>Contains the acting player's identifier and the target coordinates.
 * Coordinate validation (bounds, turn, game state) is handled in the service layer.
 *
 * @param shooterId identifier of the player firing the shot
 * @param x x-coordinate of the shot (0-based)
 * @param y y-coordinate of the shot (0-based)
 */
public record ShotRequest(
        UUID shooterId,
        int x,
        int y
) { }
