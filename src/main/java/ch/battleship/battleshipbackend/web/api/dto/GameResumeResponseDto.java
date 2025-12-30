package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

public record GameResumeResponseDto(
        String gameCode,
        GameStatus status,
        boolean handshakeComplete,        // status == RUNNING
        String requestedByPlayerName,
        String currentTurnPlayerName,
        GameSnapshotDto snapshot
) {}