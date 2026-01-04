package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.ShipPlacement;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;

/**
 * DTO representing a single ship placement on a board.
 *
 * <p>This DTO is primarily used for the player's own board during setup (and for dev/test inspection).
 * It describes what ship is placed, where it starts and how it is oriented.
 *
 * @param type ship type (defines the ship class/length)
 * @param startX start x-coordinate (0-based)
 * @param startY start y-coordinate (0-based)
 * @param orientation placement orientation (horizontal/vertical)
 * @param size ship length in fields (redundant but convenient for clients)
 */
public record ShipPlacementDto(
        ShipType type,
        int startX,
        int startY,
        Orientation orientation,
        int size
) {

    /**
     * Creates a {@code ShipPlacementDto} from the given domain {@link ShipPlacement}.
     *
     * @param placement domain ship placement
     * @return mapped DTO
     * @throws NullPointerException if {@code placement} or nested ship/start is null
     */
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
