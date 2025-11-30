package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.service.GameService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GameShootingTest {

    private Game createGameWithTwoPlayersAndOneBoardWithOneShip() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game("TEST-CODE", config);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");

        game.addPlayer(attacker);
        game.addPlayer(defender);

        Board defenderBoard = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                defender
        );

        // Schiff: DESTROYER (size 2) horizontal bei (3,3) -> (4,3)
        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        return game;
    }

    private Player getAttacker(Game game) {
        return game.getPlayers().get(0);
    }

    private Board getDefenderBoard(Game game) {
        return game.getBoards().get(0);
    }

    @Test
    void fireShot_shouldReturnMiss_whenNoShipAtCoordinate() {
        // Arrange
        Game game = createGameWithTwoPlayersAndOneBoardWithOneShip();
        Player attacker = getAttacker(game);
        Board defenderBoard = getDefenderBoard(game);

        Coordinate missCoord = new Coordinate(0, 0);

        // Act
        Shot shot = game.fireShot(attacker, defenderBoard, missCoord);

        // Assert
        assertThat(shot.getResult()).isEqualTo(ShotResult.MISS);
        assertThat(game.getShots()).hasSize(1);
    }

    @Test
    void fireShot_shouldReturnHit_whenShipIsHitButNotSunk() {
        // Arrange
        Game game = createGameWithTwoPlayersAndOneBoardWithOneShip();
        Player attacker = getAttacker(game);
        Board defenderBoard = getDefenderBoard(game);

        Coordinate hitCoord = new Coordinate(3, 3);

        // Act
        Shot shot = game.fireShot(attacker, defenderBoard, hitCoord);

        // Assert
        assertThat(shot.getResult()).isEqualTo(ShotResult.HIT);
        assertThat(game.getShots()).hasSize(1);
    }

    @Test
    void fireShot_shouldReturnSunk_whenAllCoordinatesOfShipAreHit() {
        // Arrange
        Game game = createGameWithTwoPlayersAndOneBoardWithOneShip();
        Player attacker = getAttacker(game);
        Board defenderBoard = getDefenderBoard(game);

        // Erstes Feld treffen
        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3));

        // Zweites Feld treffen -> Schiff sollte versenkt sein
        Shot shot2 = game.fireShot(attacker, defenderBoard, new Coordinate(4, 3));

        // Assert
        assertThat(shot2.getResult()).isEqualTo(ShotResult.SUNK);
        assertThat(game.getShots()).hasSize(2);
    }

    @Test
    void fireShot_shouldReturnAlreadyShot_whenCoordinateWasShotBefore() {
        // Arrange
        Game game = createGameWithTwoPlayersAndOneBoardWithOneShip();
        Player attacker = getAttacker(game);
        Board defenderBoard = getDefenderBoard(game);

        Coordinate coord = new Coordinate(0, 0);

        // Erster Schuss
        Shot first = game.fireShot(attacker, defenderBoard, coord);
        assertThat(first.getResult()).isEqualTo(ShotResult.MISS);

        // Act: zweiter Schuss auf dieselbe Koordinate
        Shot second = game.fireShot(attacker, defenderBoard, coord);

        // Assert
        assertThat(second.getResult()).isEqualTo(ShotResult.ALREADY_SHOT);
        assertThat(game.getShots()).hasSize(2);
    }
}
