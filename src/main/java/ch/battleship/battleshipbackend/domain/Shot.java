package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single shot fired during a game.
 *
 * <p>A shot records the target coordinate, the computed result (miss/hit/sunk, etc.),
 * the shooter and the targeted board. The shot history is used to reconstruct the game
 * state and validate repeated shots.
 */
@Entity
@Table(name = "shots")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shot extends BaseEntity {

    /**
     * Target coordinate of the shot (0-based).
     */
    @Embedded
    private Coordinate coordinate;

    /**
     * Outcome of the shot (e.g. MISS, HIT, SUNK).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShotResult result;

    /**
     * The player who fired the shot.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "shooter_id")
    private Player shooter;

    /**
     * The board that was targeted by the shot.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "target_board_id")
    private Board targetBoard;

    /**
     * Creates a new shot entry.
     *
     * @param coordinate target coordinate
     * @param result computed shot result
     * @param shooter player who fired the shot
     * @param targetBoard board that was targeted
     */
    public Shot(Coordinate coordinate, ShotResult result, Player shooter, Board targetBoard) {
        this.coordinate = coordinate;
        this.result = result;
        this.shooter = shooter;
        this.targetBoard = targetBoard;
    }
}
