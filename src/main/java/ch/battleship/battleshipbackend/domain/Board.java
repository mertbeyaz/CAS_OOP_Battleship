package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a player's board including ship placements.
 *
 * <p>The board has a fixed size (width/height) and belongs to exactly one owner.
 * Ship placements can be modified until the board is locked (confirmed by the player).
 */
@Entity
@Table(name = "boards")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board extends BaseEntity {

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private Player owner;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "board_id") // FK in ship_placements
    private List<ShipPlacement> placements = new ArrayList<>();

    /**
     * If {@code true}, the board was confirmed by the owner and must not be modified anymore.
     */
    @Column(nullable = false)
    private boolean locked = false;

    public Board(int width, int height, Player owner) {
        this.width = width;
        this.height = height;
        this.owner = owner;
    }

    /**
     * Removes all ship placements from this board.
     *
     * <p>Intended for re-roll / re-arrange during the setup phase.
     */
    public void clearPlacements() {
        this.placements.clear();
    }

    /**
     * Checks whether a ship can be placed at the given start coordinate and orientation.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>The ship must stay within the board boundaries.</li>
     *   <li>The ship must not overlap with existing placements.</li>
     * </ul>
     *
     * @param ship the ship to place
     * @param start the starting coordinate (top-left reference depending on orientation)
     * @param orientation the placement orientation
     * @return {@code true} if placement is valid, otherwise {@code false}
     */
    public boolean canPlaceShip(Ship ship, Coordinate start, Orientation orientation) {
        ShipPlacement candidate = new ShipPlacement(ship, start, orientation);
        List<Coordinate> newCoords = candidate.getCoveredCoordinates();

        // 1) Validate board boundaries
        for (Coordinate c : newCoords) {
            if (c.getX() < 0 || c.getX() >= width ||
                    c.getY() < 0 || c.getY() >= height) {
                return false;
            }
        }

        // 2) Validate overlap with existing placements
        Set<Coordinate> newCoordSet = new HashSet<>(newCoords);

        boolean overlaps = placements.stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .anyMatch(newCoordSet::contains);

        return !overlaps;
    }

    /**
     * Places a ship on the board if the placement is valid.
     *
     * @param ship the ship to place
     * @param start the starting coordinate
     * @param orientation the placement orientation
     * @return the created {@link ShipPlacement}
     * @throws IllegalStateException if the placement violates board rules (out of bounds or overlaps)
     */
    public ShipPlacement placeShip(Ship ship, Coordinate start, Orientation orientation) {
        if (!canPlaceShip(ship, start, orientation)) {
            throw new IllegalStateException("Cannot place ship at given position");
        }

        ShipPlacement placement = new ShipPlacement(ship, start, orientation);
        placements.add(placement);
        return placement;
    }
}
