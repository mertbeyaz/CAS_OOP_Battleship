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

@Entity
@Table(name = "ship_placements")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipPlacement extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "ship_id")
    private Ship ship;

    @Embedded
    private Coordinate start;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Orientation orientation;

    public ShipPlacement(Ship ship, Coordinate start, Orientation orientation) {
        this.ship = ship;
        this.start = start;
        this.orientation = orientation;
    }

    /**
     * Liefert alle Koordinaten, die dieses Schiff auf dem Board abdeckt.
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
