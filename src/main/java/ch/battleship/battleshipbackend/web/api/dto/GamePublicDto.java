package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

/**
 * Public, client-safe view of a game for a specific player.
 *
 * <p>This DTO is intentionally minimal and contains only the information needed for the UI
 * without revealing hidden game data (e.g. ship placements, full shot history, internal ids).
 *
 * <p>The values are computed from the perspective of the requesting player:
 * <ul>
 *   <li>{@code yourBoardLocked} and {@code opponentBoardLocked} indicate setup progress</li>
 *   <li>{@code yourTurn} tells the client whether the player may shoot</li>
 *   <li>{@code opponentName} is provided for display purposes (may be null if opponent not present yet)</li>
 * </ul>
 *
 * @param gameCode public identifier of the game
 * @param status current game status
 * @param yourBoardLocked whether the requesting player's board is confirmed/locked
 * @param opponentBoardLocked whether the opponent's board is confirmed/locked (if opponent exists)
 * @param yourTurn whether it is currently the requesting player's turn (only relevant in RUNNING)
 * @param opponentName opponent username (null if no opponent joined yet)
 */
public record GamePublicDto(
        String gameCode,
        GameStatus status,
        boolean yourBoardLocked,
        boolean opponentBoardLocked,
        boolean yourTurn,
        String opponentName
) {}
