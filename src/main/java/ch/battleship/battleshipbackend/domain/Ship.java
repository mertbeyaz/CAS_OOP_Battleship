package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a ship instance placed on a board.
 *
 * <p>The ship type defines its size and is persisted as an enum value.
 * The size is derived from the type and therefore not stored as a separate column.
 */
@Entity
@Table(name = "ships")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ship extends BaseEntity {

    /**
     * Ship type defining the ship size.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipType type;

    /**
     * Returns the size of the ship derived from its type.
     *
     * <p>Marked as {@code @Transient} because the value is computed and does not need persistence.
     *
     * @return number of board cells occupied by this ship
     */
    @Transient
    public int getSize() {
        return type.getSize();
    }

    /**
     * Creates a new ship of the given type.
     *
     * @param type ship type
     */
    public Ship(ShipType type) {
        this.type = type;
    }
}
