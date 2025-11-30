package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shots")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shot extends BaseEntity {

    @Embedded
    private Coordinate coordinate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShotResult result;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shooter_id")
    private Player shooter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_board_id")
    private Board targetBoard;

    public Shot(Coordinate coordinate, ShotResult result, Player shooter, Board targetBoard) {
        this.coordinate = coordinate;
        this.result = result;
        this.shooter = shooter;
        this.targetBoard = targetBoard;
    }
}

