package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a ship placement on a board.
 *
 * <p>A placement consists of a ship, a start coordinate and an orientation.
 * The covered coordinates are derived from these values.
 */
@Entity
@Table(name = "ship_placements")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipPlacement extends BaseEntity {

    /**
     * The ship that is placed.
     */
    @ManyToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "ship_id")
    private Ship ship;

    /**
     * Start coordinate of the ship placement (0-based).
     */
    @Embedded
    private Coordinate start;

    /**
     * Orientation of the ship placement (horizontal or vertical).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Orientation orientation;

    /**
     * Creates a new ship placement.
     *
     * @param ship the ship to place
     * @param start start coordinate (0-based)
     * @param orientation placement orientation
     */
    public ShipPlacement(Ship ship, Coordinate start, Orientation orientation) {
        this.ship = ship;
        this.start = start;
        this.orientation = orientation;
    }

    /**
     * Returns all coordinates covered by this placement on the board.
     *
     * <p>The coordinates are derived from the ship size, the start coordinate and the orientation.
     *
     * @return list of covered coordinates (size equals ship size)
     */
    public List<Coordinate> getCoveredCoordinates() {
        List<Coordinate> coords = new ArrayList<>();
        int size = ship.getSize();

        for (int i = 0; i < size; i++) {
            int x = start.getX() + (orientation == Orientation.HORIZONTAL ? i : 0);
            int y = start.getY() + (orientation == Orientation.VERTICAL ? i : 0);
            coords.add(new Coordinate(x, y));
        }

        return coords;
    }
}
