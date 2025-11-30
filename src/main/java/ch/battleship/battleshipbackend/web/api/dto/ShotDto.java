package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Shot;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;

import java.util.UUID;

public record ShotDto(
        UUID id,
        UUID shooterId,
        UUID targetBoardId,
        int x,
        int y,
        ShotResult result
) {
    public static ShotDto from(Shot shot) {
        return new ShotDto(
                shot.getId(),
                shot.getShooter().getId(),
                shot.getTargetBoard().getId(),
                shot.getCoordinate().getX(),
                shot.getCoordinate().getY(),
                shot.getResult()
        );
    }
}
