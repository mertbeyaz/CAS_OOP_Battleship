package ch.battleship.battleshipbackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Embeddable configuration for a game instance.
 *
 * <p>Defines board dimensions and the fleet setup used during the setup phase.
 * Stored as part of the owning {@code Game} entity.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameConfiguration {

    /**
     * Board width in cells.
     */
    @Column(nullable = false)
    private int boardWidth;

    /**
     * Board height in cells.
     */
    @Column(nullable = false)
    private int boardHeight;

    /**
     * Minimum spacing rule between ships (if enforced by placement logic).
     */
    @Column(nullable = false)
    private int shipMargin;

    /**
     * Fleet definition string (e.g. "2x2,2x3,1x4,1x5").
     *
     * <p>Format: {@code <count>x<size>} separated by commas.
     * This allows defining the fleet without changing code.
     */
    @Column(nullable = false, length = 100)
    private String fleetDefinition;

    private GameConfiguration(int boardWidth, int boardHeight, int shipMargin, String fleetDefinition) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        this.shipMargin = shipMargin;
        this.fleetDefinition = fleetDefinition;
    }

    /**
     * Returns the default game configuration used by the application.
     *
     * @return default configuration (10x10 board, predefined fleet)
     */
    public static GameConfiguration defaultConfig() {
        return new GameConfiguration(
                10,
                10,
                2,
                "2x2,2x3,1x4,1x5"
        );
    }
}
