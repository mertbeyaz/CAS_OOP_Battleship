package ch.battleship.battleshipbackend.domain.enums;

/**
 * Defines the available ship types and their corresponding sizes.
 *
 * <p>The size represents the number of board cells occupied by the ship.
 */
public enum ShipType {

    /**
     * Ship occupying 2 cells.
     */
    DESTROYER(2),

    /**
     * Ship occupying 3 cells.
     */
    CRUISER(3),

    /**
     * Ship occupying 4 cells.
     */
    BATTLESHIP(4),

    /**
     * Ship occupying 5 cells.
     */
    CARRIER(5);

    /**
     * Number of board cells occupied by the ship.
     */
    private final int size;

    ShipType(int size) {
        this.size = size;
    }

    /**
     * Returns the size of the ship.
     *
     * @return number of board cells occupied by the ship
     */
    public int getSize() {
        return size;
    }
}
