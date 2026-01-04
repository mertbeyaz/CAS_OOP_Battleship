package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.ShotResult;

/**
 * DTO returned after firing a shot.
 *
 * <p>This DTO summarizes the outcome of a shot in a client-friendly form so the frontend
 * can easily update the UI without reinterpreting domain logic.
 *
 * <p>The boolean flags are derived from {@link ShotResult} for convenience:
 * <ul>
 *   <li>{@code hit} is {@code true} for HIT and SUNK</li>
 *   <li>{@code shipSunk} is {@code true} only for SUNK</li>
 *   <li>{@code yourTurn} indicates whether the player may shoot again</li>
 * </ul>
 *
 * @param result raw shot result enum
 * @param hit whether the shot hit a ship
 * @param shipSunk whether a ship was sunk by this shot
 * @param yourTurn whether it is still the player's turn after the shot
 */
public record ShotResultDto(
        ShotResult result,
        boolean hit,
        boolean shipSunk,
        boolean yourTurn
) {}
