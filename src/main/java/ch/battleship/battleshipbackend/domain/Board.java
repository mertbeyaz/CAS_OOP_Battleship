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
     * When locked, the owner confirmed the board and it must not be modified anymore.
     */
    @Column(nullable = false)
    private boolean locked = false;

    public Board(int width, int height, Player owner) {
        this.width = width;
        this.height = height;
        this.owner = owner;
    }

    public void clearPlacements() {
        this.placements.clear();
    }

    /**
     * Prüft, ob ein Schiff an der gewünschten Position platziert werden kann
     * (Boardgrenzen + keine Überlappung).
     */
    public boolean canPlaceShip(Ship ship, Coordinate start, Orientation orientation) {
        ShipPlacement candidate = new ShipPlacement(ship, start, orientation);
        List<Coordinate> newCoords = candidate.getCoveredCoordinates();

        // 1) Board-Grenzen prüfen (wie bisher)
        for (Coordinate c : newCoords) {
            if (c.getX() < 0 || c.getX() >= width ||
                    c.getY() < 0 || c.getY() >= height) {
                return false;
            }
        }

        // 2) Überlappung mit existierenden Placements prüfen (funktional)
        Set<Coordinate> newCoordSet = new HashSet<>(newCoords);

        boolean overlaps = placements.stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .anyMatch(newCoordSet::contains);

        if (overlaps) {
            return false;
        }

        return true;
    }

    /**
     * Platziert ein Schiff, oder wirft eine IllegalStateException,
     * wenn die Platzierung nicht erlaubt ist.
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

