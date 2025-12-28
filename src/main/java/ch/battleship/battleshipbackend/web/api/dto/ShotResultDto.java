package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.enums.ShotResult;

public record ShotResultDto(
        ShotResult result,
        boolean hit,
        boolean shipSunk,
        boolean yourTurn
) {}
