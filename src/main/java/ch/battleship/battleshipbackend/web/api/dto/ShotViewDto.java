package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Shot;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;

public record ShotViewDto(
        int x,
        int y,
        ShotResult result
) {
    public static ShotViewDto from(Shot s) {
        return new ShotViewDto(
                s.getCoordinate().getX(),
                s.getCoordinate().getY(),
                s.getResult()
        );
    }
}
