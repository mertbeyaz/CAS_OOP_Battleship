package ch.battleship.battleshipbackend.web.api.dto;


import ch.battleship.battleshipbackend.domain.ShipPlacement;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;

public record ShipPlacementDto(
        ShipType type,
        int startX,
        int startY,
        Orientation orientation,
        int size
) {
    public static ShipPlacementDto from(ShipPlacement placement) {
        return new ShipPlacementDto(
                placement.getShip().getType(),
                placement.getStart().getX(),
                placement.getStart().getY(),
                placement.getOrientation(),
                placement.getShip().getType().getSize()
        );
    }

}
