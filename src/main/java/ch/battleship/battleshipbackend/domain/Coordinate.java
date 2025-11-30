package ch.battleship.battleshipbackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordinate {

    @Column(nullable = false)
    private int x; // 0-basiert: 0..width-1

    @Column(nullable = false)
    private int y; // 0-basiert: 0..height-1

    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Value-Object-Equals (optional aber nice)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coordinate other)) return false;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
