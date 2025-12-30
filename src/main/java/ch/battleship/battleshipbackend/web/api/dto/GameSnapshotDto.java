package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

import java.util.List;

public record GameSnapshotDto(
        String gameCode,
        GameStatus status,
        int boardWidth,
        int boardHeight,

        String youName,
        String opponentName,     // ok, aber keine opponentId

        boolean yourBoardLocked,
        boolean opponentBoardLocked,
        boolean yourTurn,

        // Dein Board: inkl. Ships/Placements
        BoardStateDto yourBoard,

        // Shots die auf DEIN Board gegangen sind (vom Gegner) inkl Result (HIT/MISS/SUNK)
        List<ShotViewDto> shotsOnYourBoard,

        // Shots die DU auf den Gegner abgegeben hast inkl Result -> Target-Grid rendern
        List<ShotViewDto> yourShotsOnOpponent
) {}
