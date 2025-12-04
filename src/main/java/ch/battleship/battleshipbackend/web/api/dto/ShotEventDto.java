package ch.battleship.battleshipbackend.web.api.dto;


import ch.battleship.battleshipbackend.domain.enums.GameStatus;

public record ShotEventDto(
        String gameCode,
        String attackerName,
        String defenderName,
        String coordinate,   // z.B. "B5"
        boolean hit,
        boolean shipSunk,
        GameStatus gameStatus,
        String nextPlayerName
) {
}