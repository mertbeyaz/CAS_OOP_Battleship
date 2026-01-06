package ch.battleship.battleshipbackend.web.api.dto;

import java.util.UUID;

/**
 * Generic request DTO for player-initiated game actions.
 *
 * <p>This DTO is used for endpoints where only the acting player's identity
 * is required (e.g. pause, forfeit).
 *
 * @param playerId identifier of the player performing the action
 */
public record PlayerActionRequest(
        UUID playerId
) {}
