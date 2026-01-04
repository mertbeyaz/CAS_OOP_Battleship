package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain-level tests for the core shooting mechanics in {@link Game#fireShot(Player, Board, Coordinate)}.
 *
 * <p><b>Scope:</b>
 * <ul>
 *   <li>Shot result evaluation: {@link ShotResult#MISS}, {@link ShotResult#HIT}, {@link ShotResult#SUNK},
 *       {@link ShotResult#ALREADY_SHOT}</li>
 *   <li>Correct append behavior: shots are stored in {@link Game#getShots()}</li>
 * </ul>
 *
 * <p><b>Out of scope:</b>
 * <ul>
 *   <li>Turn switching logic (handled in {@link ch.battleship.battleshipbackend.service.GameService#fireShot})</li>
 *   <li>Persistence / repositories</li>
 *   <li>WebSocket events</li>
 * </ul>
 *
 * <p>Test setup uses a minimal scenario:
 * two players, one defender board, and exactly one ship (DESTROYER size 2) placed horizontally at (3,3)-(4,3).
 */
class GameShootingTest {

    /**
     * Creates a minimal game setup for shooting tests:
     * <ul>
     *   <li>2 players: attacker and defender</li>
     *   <li>1 board: defender's board</li>
     *   <li>1 ship: DESTROYER horizontally at (3,3)-(4,3)</li>
     * </ul>
     */
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

        // Ship: DESTROYER (size 2) horizontal at (3,3) -> covers (3,3) and (4,3)
        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        return game;
    }

    /**
     * Convenience accessor: the first player is considered the attacker in this test setup.
     */
    private Player getAttacker(Game game) {
        return game.getPlayers().get(0);
    }

    /**
     * Convenience accessor: the only board in this setup is the defender's board.
     */
    private Board getDefenderBoard(Game game) {
        return game.getBoards().get(0);
    }

    /**
     * Shooting at an empty coordinate must produce {@link ShotResult#MISS}.
     */
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

    /**
     * Shooting a ship coordinate should produce {@link ShotResult#HIT} if the ship is not sunk yet.
     */
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

    /**
     * A ship becomes sunk when all its covered coordinates have been hit.
     * For a DESTROYER (size 2), hitting both cells should result in {@link ShotResult#SUNK} on the second hit.
     */
    @Test
    void fireShot_shouldReturnSunk_whenAllCoordinatesOfShipAreHit() {
        // Arrange
        Game game = createGameWithTwoPlayersAndOneBoardWithOneShip();
        Player attacker = getAttacker(game);
        Board defenderBoard = getDefenderBoard(game);

        // First hit
        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3));

        // Act: second hit -> should sink the ship
        Shot shot2 = game.fireShot(attacker, defenderBoard, new Coordinate(4, 3));

        // Assert
        assertThat(shot2.getResult()).isEqualTo(ShotResult.SUNK);
        assertThat(game.getShots()).hasSize(2);
    }

    /**
     * Shooting the same coordinate multiple times should return {@link ShotResult#ALREADY_SHOT} on subsequent attempts.
     */
    @Test
    void fireShot_shouldReturnAlreadyShot_whenCoordinateWasShotBefore() {
        // Arrange
        Game game = createGameWithTwoPlayersAndOneBoardWithOneShip();
        Player attacker = getAttacker(game);
        Board defenderBoard = getDefenderBoard(game);

        Coordinate coord = new Coordinate(0, 0);

        // First shot
        Shot first = game.fireShot(attacker, defenderBoard, coord);
        assertThat(first.getResult()).isEqualTo(ShotResult.MISS);

        // Act: second shot on same coordinate
        Shot second = game.fireShot(attacker, defenderBoard, coord);

        // Assert
        assertThat(second.getResult()).isEqualTo(ShotResult.ALREADY_SHOT);
        assertThat(game.getShots()).hasSize(2);
    }
}
