package ch.battleship.battleshipbackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameConfiguration {

    @Column(nullable = false)
    private int boardWidth;

    @Column(nullable = false)
    private int boardHeight;

    @Column(nullable = false)
    private int shipMargin;

    @Column(nullable = false, length = 100)
    private String fleetDefinition;
    // z.B. "2x2,2x3,1x4,1x5" – später kann man das parsen

    private GameConfiguration(int boardWidth, int boardHeight, int shipMargin, String fleetDefinition) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        this.shipMargin = shipMargin;
        this.fleetDefinition = fleetDefinition;
    }

    public static GameConfiguration defaultConfig() {
        return new GameConfiguration(
                10,
                10,
                2,
                "2x2,2x3,1x4,1x5"
        );
    }
}

