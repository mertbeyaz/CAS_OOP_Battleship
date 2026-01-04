package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Shot;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;

/**
 * Lightweight DTO representing a shot on a board.
 *
 * <p>This DTO is used in game snapshots to render shot markers on the UI grid.
 * It intentionally contains only coordinate and result information and does not
 * expose shooter or target identifiers.
 *
 * @param x x-coordinate of the shot (0-based)
 * @param y y-coordinate of the shot (0-based)
 * @param result result of the shot (MISS, HIT, SUNK, ALREADY_SHOT)
 */
public record ShotViewDto(
        int x,
        int y,
        ShotResult result
) {

    /**
     * Creates a {@code ShotViewDto} from the given domain {@link Shot}.
     *
     * @param s domain shot entity
     * @return mapped DTO
     * @throws NullPointerException if {@code s} or its coordinate is null
     */
    public static ShotViewDto from(Shot s) {
        return new ShotViewDto(
                s.getCoordinate().getX(),
                s.getCoordinate().getY(),
                s.getResult()
        );
    }
}
