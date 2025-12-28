package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;

public record GamePublicDto(
        String gameCode,
        GameStatus status,
        boolean yourBoardLocked,
        boolean opponentBoardLocked,
        boolean yourTurn,
        String opponentName
) {}
