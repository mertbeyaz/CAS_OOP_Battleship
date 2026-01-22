package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

import java.util.List;
import java.util.UUID;

/**
 * Player-specific snapshot of the game state.
 *
 * <p>This DTO is used for reconnect / (browser refresh in future) scenarios and provides enough information
 * for the client to rebuild the UI state without leaking hidden opponent information.
 *
 * <p>Security / anti-cheat considerations:
 * <ul>
 *   <li>The snapshot includes the requesting player's board with ship placements.</li>
 *   <li>It does NOT include the opponent's ship placements and intentionally does not expose an opponentId.</li>
 *   <li>Shots are split into "shots on your board" and "your shots on opponent" to support UI rendering
 *       while keeping the domain model hidden.</li>
 * </ul>
 *
 * <p>Perspective:
 * All fields are computed from the perspective of the requesting player ("you").
 *
 * @param gameCode public identifier of the game
 * @param status current game status
 * @param boardWidth configured board width
 * @param boardHeight configured board height
 * @param yourPlayerId Id of the requesting player
 * @param youName username of the requesting player
 * @param opponentName opponent username (null if opponent not present yet)
 * @param yourBoardLocked whether your board is confirmed/locked
 * @param opponentBoardLocked whether opponent board is confirmed/locked (if opponent exists)
 * @param yourTurn whether it is currently your turn (only relevant in RUNNING)
 * @param yourBoard your board state including ship placements (never the opponent's)
 * @param shotsOnYourBoard shots fired by the opponent onto your board (incl. result)
 * @param yourShotsOnOpponent shots you fired onto the opponent board (incl. result)
 */
public record GameSnapshotDto(
        String gameCode,
        GameStatus status,
        int boardWidth,
        int boardHeight,

        String yourPlayerId,
        String youName,
        String opponentName,

        boolean yourBoardLocked,
        boolean opponentBoardLocked,
        boolean yourTurn,

        BoardStateDto yourBoard,
        List<ShotViewDto> shotsOnYourBoard,
        List<ShotViewDto> yourShotsOnOpponent
) {}
