package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.ShipType;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ships")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ship extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipType type;

    // size ist vom ShipType ableitbar
    @Transient
    public int getSize() {
        return type.getSize();
    }

    public Ship(ShipType type) {
        this.type = type;
    }
}