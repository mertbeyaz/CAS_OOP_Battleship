package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

/**
 * DTO returned by the resume endpoint.
 *
 * <p>This project implements resume as a two-player handshake:
 * <ul>
 *   <li>First player requests resume -> game transitions to a waiting/handshake state</li>
 *   <li>Second player confirms resume -> game becomes RUNNING again</li>
 * </ul>
 *
 * <p>The response always includes a snapshot so the client can refresh its UI immediately
 * after a reconnect/resume attempt.
 *
 * @param gameCode public identifier of the game
 * @param status current game status after processing the request
 * @param handshakeComplete {@code true} if both players confirmed and the game is RUNNING
 * @param requestedByPlayerName username of the player who triggered this resume request
 * @param currentTurnPlayerName username of the current turn player (may be null until handshake completes)
 * @param snapshot player-specific snapshot of the game state
 */
public record GameResumeResponseDto(
        String gameCode,
        GameStatus status,
        boolean handshakeComplete,
        String requestedByPlayerName,
        String currentTurnPlayerName,
        GameSnapshotDto snapshot
) {}
