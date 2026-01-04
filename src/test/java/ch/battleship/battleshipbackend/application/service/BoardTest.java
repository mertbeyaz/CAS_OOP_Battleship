package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Board}.
 *
 * <p>Focus: ship placement rules (bounds + overlap) and correct placement creation.
 * These tests validate the core board invariants without involving persistence or services.
 */
class BoardTest {

    /**
     * Creates a 10x10 board with a dummy owner.
     *
     * <p>The owner does not need to be persisted for these unit tests, because the board logic
     * is purely in-memory.
     */
    private Board createEmptyBoard() {
        Player owner = new Player("TestPlayer");
        return new Board(10, 10, owner);
    }

    @Test
    void canPlaceShip_shouldReturnTrue_whenShipFitsAndNoOverlap() {
        // Arrange
        Board board = createEmptyBoard();
        Ship ship = new Ship(ShipType.DESTROYER); // size 2
        Coordinate start = new Coordinate(3, 3);

        // Act
        boolean resultHorizontal = board.canPlaceShip(ship, start, Orientation.HORIZONTAL);
        boolean resultVertical = board.canPlaceShip(ship, start, Orientation.VERTICAL);

        // Assert
        assertThat(resultHorizontal).isTrue();
        assertThat(resultVertical).isTrue();
    }

    @Test
    void canPlaceShip_shouldReturnFalse_whenShipExceedsRightBoundary() {
        // Arrange
        Board board = createEmptyBoard();
        Ship ship = new Ship(ShipType.BATTLESHIP); // size 4
        // Start near the right edge so the ship would exceed board bounds (10x10).
        Coordinate start = new Coordinate(8, 5);

        // Act
        boolean result = board.canPlaceShip(ship, start, Orientation.HORIZONTAL);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void canPlaceShip_shouldReturnFalse_whenShipExceedsBottomBoundary() {
        // Arrange
        Board board = createEmptyBoard();
        Ship ship = new Ship(ShipType.BATTLESHIP); // size 4
        // Start near the bottom edge so the ship would exceed board bounds (10x10).
        Coordinate start = new Coordinate(5, 8);

        // Act
        boolean result = board.canPlaceShip(ship, start, Orientation.VERTICAL);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void canPlaceShip_shouldReturnFalse_whenShipOverlapsExistingPlacement() {
        // Arrange
        Board board = createEmptyBoard();
        Ship ship = new Ship(ShipType.CRUISER); // size 3
        Ship other = new Ship(ShipType.DESTROYER); // size 2

        // Place first ship: horizontal from (2,2) -> (4,2)
        board.placeShip(ship, new Coordinate(2, 2), Orientation.HORIZONTAL);

        // Second ship would overlap at (3,2) (vertical from (3,1) -> (3,2))
        Coordinate overlappingStart = new Coordinate(3, 1);

        // Act
        boolean result = board.canPlaceShip(other, overlappingStart, Orientation.VERTICAL);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void placeShip_shouldAddPlacement_whenValid() {
        // Arrange
        Board board = createEmptyBoard();
        Ship ship = new Ship(ShipType.DESTROYER);
        Coordinate start = new Coordinate(1, 1);

        // Act
        ShipPlacement placement = board.placeShip(ship, start, Orientation.HORIZONTAL);

        // Assert
        assertThat(board.getPlacements()).hasSize(1);
        assertThat(board.getPlacements().get(0)).isEqualTo(placement);

        assertThat(placement.getShip()).isEqualTo(ship);
        assertThat(placement.getStart().getX()).isEqualTo(1);
        assertThat(placement.getStart().getY()).isEqualTo(1);
        assertThat(placement.getOrientation()).isEqualTo(Orientation.HORIZONTAL);
    }

    @Test
    void placeShip_shouldThrowException_whenInvalidPlacement() {
        // Arrange
        Board board = createEmptyBoard();
        Ship ship = new Ship(ShipType.CARRIER); // size 5
        Coordinate startOutOfBounds = new Coordinate(9, 9); // guaranteed out of bounds

        // Act + Assert
        assertThatThrownBy(() ->
                board.placeShip(ship, startOutOfBounds, Orientation.HORIZONTAL)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot place ship");

        assertThat(board.getPlacements()).isEmpty();
    }
}
