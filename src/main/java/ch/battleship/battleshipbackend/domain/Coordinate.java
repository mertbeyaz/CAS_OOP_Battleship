package ch.battleship.battleshipbackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable value object representing a coordinate on the game board.
 *
 * <p>Coordinates are 0-based (x: 0..width-1, y: 0..height-1) and are used for ship placement
 * and shot targeting. Implements {@link #equals(Object)} and {@link #hashCode()} to support
 * set operations (e.g. overlap detection).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordinate {

    /**
     * 0-based x coordinate (0..width-1).
     */
    @Column(nullable = false)
    private int x;

    /**
     * 0-based y coordinate (0..height-1).
     */
    @Column(nullable = false)
    private int y;

    /**
     * Creates a coordinate with 0-based indices.
     *
     * @param x 0-based x coordinate
     * @param y 0-based y coordinate
     */
    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Value-object equality based on coordinate values.
     *
     * @param o other object
     * @return {@code true} if both coordinates share the same x/y values
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coordinate other)) return false;
        return x == other.x && y == other.y;
    }

    /**
     * Hash code consistent with {@link #equals(Object)} to allow usage in hash-based collections.
     *
     * @return hash code based on x/y
     */
    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
