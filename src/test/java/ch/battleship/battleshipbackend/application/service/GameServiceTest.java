package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;
import ch.battleship.battleshipbackend.web.api.dto.GamePublicDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GameService}.
 *
 * <p>Focus areas:
 * <ul>
 *   <li>Game creation and default configuration</li>
 *   <li>Joining a game (player limits, state transitions, board + fleet creation)</li>
 *   <li>Firing shots (validation, persistence interactions, hit/miss)</li>
 *   <li>Public state robustness (no null crashes in early states)</li>
 *   <li>Dev endpoints helper methods (board state + ASCII rendering)</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Tests verify both domain effects (state, collections) and repository interactions.</li>
 *   <li>IDs are simulated via {@link ch.battleship.battleshipbackend.testutil.EntityTestUtils#setId(Object, UUID)}
 *       because normally they are assigned by persistence.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ShotRepository shotRepository;

    @InjectMocks
    private GameService gameService;

    // ------------------------------------------------------------------------------------
    // createNewGame
    // ------------------------------------------------------------------------------------

    @Test
    void createNewGame_shouldSetWaitingStatusAndGenerateGameCode_andSaveGame() {
        // Arrange
        when(gameRepository.save(any(Game.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);

        // Act
        Game game = gameService.createNewGame();

        // Assert: return value
        assertThat(game).isNotNull();
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(game.getGameCode()).isNotBlank();

        // Assert: repository interaction
        verify(gameRepository, times(1)).save(gameCaptor.capture());
        Game saved = gameCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(saved.getGameCode()).isNotBlank();
    }

    @Test
    void createNewGame_shouldUseDefaultConfiguration() {
        // Arrange
        when(gameRepository.save(any(Game.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Game game = gameService.createNewGame();

        // Assert
        assertThat(game.getConfig()).isNotNull();
        assertThat(game.getConfig().getBoardWidth()).isEqualTo(10);
        assertThat(game.getConfig().getBoardHeight()).isEqualTo(10);
        assertThat(game.getConfig().getFleetDefinition()).isEqualTo("2x2,2x3,1x4,1x5");
    }

    // ------------------------------------------------------------------------------------
    // getByGameCode
    // ------------------------------------------------------------------------------------

    @Test
    void getByGameCode_shouldDelegateToRepository() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String gameCode = "TEST-CODE";
        Game existing = new Game(gameCode, config);

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.of(existing));

        // Act
        Optional<Game> result = gameService.getByGameCode(gameCode);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getGameCode()).isEqualTo(gameCode);

        verify(gameRepository, times(1)).findByGameCode(gameCode);
        verifyNoMoreInteractions(gameRepository);
    }

    // ------------------------------------------------------------------------------------
    // joinGame
    // ------------------------------------------------------------------------------------

    @Test
    void joinGame_firstPlayer_shouldKeepStatusWaiting_andCreateBoard() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config); // status = WAITING

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Game result = gameService.joinGame(code, "Player1");

        // Assert: player created
        assertThat(result.getPlayers()).hasSize(1);
        Player p1 = result.getPlayers().get(0);
        assertThat(p1.getUsername()).isEqualTo("Player1");

        // Assert: board created and linked
        assertThat(result.getBoards()).hasSize(1);
        Board b1 = result.getBoards().get(0);
        assertThat(b1.getOwner()).isEqualTo(p1);
        assertThat(b1.getWidth()).isEqualTo(10);
        assertThat(b1.getHeight()).isEqualTo(10);

        // Status stays WAITING (only first player joined)
        assertThat(result.getStatus()).isEqualTo(GameStatus.WAITING);

        verify(gameRepository, times(1)).save(game);
    }

    @Test
    void joinGame_secondPlayer_shouldSetStatusSetup_andCreateSecondBoard() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        // consistent start state: 1 player + 1 board already in game
        Player existing = new Player("Player1");
        game.addPlayer(existing);
        game.addBoard(new Board(10, 10, existing));

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Game result = gameService.joinGame(code, "Player2");

        // Assert
        assertThat(result.getPlayers()).hasSize(2);
        Player p2 = result.getPlayers().get(1);
        assertThat(p2.getUsername()).isEqualTo("Player2");

        assertThat(result.getBoards()).hasSize(2);
        assertThat(result.getBoards()).anyMatch(b -> b.getOwner().equals(p2));

        // after second player join -> SETUP
        assertThat(result.getStatus()).isEqualTo(GameStatus.SETUP);

        verify(gameRepository, times(1)).save(game);
    }

    @Test
    void joinGame_thirdPlayer_shouldThrowIllegalStateException_andNotChangeBoards() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        Player p1 = new Player("Player1");
        Player p2 = new Player("Player2");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.addBoard(new Board(10, 10, p1));
        game.addBoard(new Board(10, 10, p2));

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act + Assert
        assertThatThrownBy(() -> gameService.joinGame(code, "Player3"))
                .isInstanceOf(IllegalStateException.class);

        // no mutation expected (third join rejected)
        assertThat(game.getPlayers()).hasSize(2);
        assertThat(game.getBoards()).hasSize(2);

        verify(gameRepository, never()).save(any());
    }

    @Test
    void joinGame_nonExistingGame_shouldThrowEntityNotFound() {
        // Arrange
        String code = "UNKNOWN";
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> gameService.joinGame(code, "Player1"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(gameRepository, never()).save(any());
    }

    @Test
    void joinGame_shouldAutoPlaceFleetOnBoard_accordingToDefaultConfig() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Game result = gameService.joinGame(code, "Player1");

        // Assert
        assertThat(result.getBoards()).hasSize(1);
        Board board = result.getBoards().get(0);

        // 1) placements exist
        assertThat(board.getPlacements())
                .as("ShipPlacements must exist on the board")
                .isNotEmpty();

        // 2) default fleet: "2x2,2x3,1x4,1x5"
        var types = board.getPlacements().stream()
                .map(p -> p.getShip().getType())
                .toList();

        assertThat(types)
                .containsExactlyInAnyOrder(
                        ShipType.DESTROYER, ShipType.DESTROYER,
                        ShipType.CRUISER, ShipType.CRUISER,
                        ShipType.BATTLESHIP,
                        ShipType.CARRIER
                );

        // 3) all coords inside bounds and non-overlapping
        var allCoords = board.getPlacements().stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .toList();

        assertThat(allCoords).allSatisfy(c -> {
            assertThat(c.getX()).isBetween(0, board.getWidth() - 1);
            assertThat(c.getY()).isBetween(0, board.getHeight() - 1);
        });

        assertThat(allCoords)
                .as("No coordinate may be covered by more than one ship")
                .doesNotHaveDuplicates();

        // Debug helper (optional)
        // printBoard(board);
    }

    // ------------------------------------------------------------------------------------
    // fireShot (Service-Logic, validation incl. + Repo)
    // ------------------------------------------------------------------------------------

    @Test
    void fireShot_shouldReturnShotAndPersistGame_whenAllDataIsValid() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        Board defenderBoard = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                defender
        );

        // place a ship so (3,3) becomes a HIT
        Ship ship = new Ship(ShipType.DESTROYER); // size 2
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        // simulate persisted IDs
        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        setId(attacker, attackerId);
        setId(defender, defenderId);

        // set turn to attacker
        game.setCurrentTurnPlayerId(attacker.getId());

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shotRepository.save(any(Shot.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Shot shot = gameService.fireShot(code, attackerId, 3, 3);

        // Assert
        assertThat(shot).isNotNull();
        assertThat(shot.getResult()).isEqualTo(ShotResult.HIT);
        assertThat(shot.getShooter()).isEqualTo(attacker);
        assertThat(shot.getTargetBoard()).isEqualTo(defenderBoard);
        assertThat(game.getShots()).hasSize(1);

        verify(gameRepository, times(1)).findByGameCode(code);
        verify(gameRepository, times(1)).save(game);
        verify(shotRepository, times(1)).save(any(Shot.class));
        verifyNoMoreInteractions(gameRepository, shotRepository);
    }

    @Test
    void fireShot_shouldThrowEntityNotFound_whenGameDoesNotExist() {
        // Arrange
        String code = "UNKNOWN";
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> gameService.fireShot(code, UUID.randomUUID(), 0, 0))
                .isInstanceOf(EntityNotFoundException.class);

        verify(gameRepository, never()).save(any());
    }

    @Test
    void fireShot_shouldThrowIllegalState_whenGameIsNotRunning() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config); // status = WAITING

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act + Assert
        assertThatThrownBy(() -> gameService.fireShot(code, UUID.randomUUID(), 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not RUNNING");

        verify(gameRepository, never()).save(any());
    }

    @Test
    void fireShot_shouldThrowIllegalState_whenShooterIsNotPartOfGame() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player defender = new Player("Defender");
        setId(defender, UUID.randomUUID());
        game.addPlayer(defender);

        Board defenderBoard = new Board(config.getBoardWidth(), config.getBoardHeight(), defender);
        setId(defenderBoard, UUID.randomUUID());
        game.addBoard(defenderBoard);

        // shooter not part of game
        UUID shooterId = UUID.randomUUID();

        // make sure we don't fail on "current turn missing"
        game.setCurrentTurnPlayerId(shooterId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act + Assert
        assertThatThrownBy(() -> gameService.fireShot(code, shooterId, 1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Shooter does not belong");

        verifyNoInteractions(shotRepository);
    }

    @Test
    void fireShot_shouldThrowIllegalState_whenOpponentBoardMissing() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        setId(attacker, UUID.randomUUID());
        setId(defender, UUID.randomUUID());

        game.addPlayer(attacker);
        game.addPlayer(defender);

        // only attacker's board -> no opponent board found
        Board attackerBoard = new Board(config.getBoardWidth(), config.getBoardHeight(), attacker);
        setId(attackerBoard, UUID.randomUUID());
        game.addBoard(attackerBoard);

        game.setCurrentTurnPlayerId(attacker.getId());

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act + Assert
        assertThatThrownBy(() -> gameService.fireShot(code, attacker.getId(), 1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No opponent board found for this game");

        verifyNoInteractions(shotRepository);
    }

    @Test
    void fireShot_shouldThrowIllegalArgument_whenCoordinateOutOfBounds() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        setId(attacker, UUID.randomUUID());
        setId(defender, UUID.randomUUID());

        Board defenderBoard = new Board(config.getBoardWidth(), config.getBoardHeight(), defender);
        setId(defenderBoard, UUID.randomUUID());
        game.addBoard(defenderBoard);

        // ensure turn check passes
        game.setCurrentTurnPlayerId(attacker.getId());

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act + Assert
        assertThatThrownBy(() -> gameService.fireShot(code, attacker.getId(), 999, 999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of board bounds");

        verifyNoInteractions(shotRepository);
    }

    // ------------------------------------------------------------------------------------
    // Public state robustness
    // ------------------------------------------------------------------------------------

    @Test
    void getPublicState_shouldBeRobust_whenOnlyOnePlayerInWaiting() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game("TEST-CODE", config);
        game.setStatus(GameStatus.WAITING);

        Player playerA = new Player("PlayerA");
        setId(playerA, UUID.randomUUID());
        game.addPlayer(playerA);

        Board boardA = new Board(config.getBoardWidth(), config.getBoardHeight(), playerA);
        setId(boardA, UUID.randomUUID());
        game.addBoard(boardA);

        when(gameRepository.findByGameCode("TEST-CODE")).thenReturn(Optional.of(game));

        // Act
        GamePublicDto dto = gameService.getPublicState("TEST-CODE", playerA.getId());

        // Assert
        assertEquals("TEST-CODE", dto.gameCode());
        assertEquals(GameStatus.WAITING, dto.status());
        assertFalse(dto.yourTurn());
        assertFalse(dto.opponentBoardLocked());
        assertNull(dto.opponentName());
    }

    @Test
    void getPublicState_shouldNotCrash_whenOpponentExistsButHasNoBoard() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game("TEST-CODE", config);
        game.setStatus(GameStatus.SETUP);

        Player playerA = new Player("PlayerA");
        Player playerB = new Player("PlayerB");
        setId(playerA, UUID.randomUUID());
        setId(playerB, UUID.randomUUID());

        game.addPlayer(playerA);
        game.addPlayer(playerB);

        // only board for A
        Board boardA = new Board(config.getBoardWidth(), config.getBoardHeight(), playerA);
        setId(boardA, UUID.randomUUID());
        game.addBoard(boardA);

        when(gameRepository.findByGameCode("TEST-CODE")).thenReturn(Optional.of(game));

        // Act
        GamePublicDto dto = gameService.getPublicState("TEST-CODE", playerA.getId());

        // Assert
        assertEquals(GameStatus.SETUP, dto.status());
        assertFalse(dto.yourTurn());
        assertFalse(dto.opponentBoardLocked()); // no opponent board -> false
        assertEquals("PlayerB", dto.opponentName());
    }

    // ------------------------------------------------------------------------------------
    // Dev-only helpers in service (board state + ASCII)
    // ------------------------------------------------------------------------------------

    @Test
    void getBoardState_shouldReturnBoardWithShipPlacements() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        Player defender = new Player("Defender");
        game.addPlayer(defender);

        Board board = new Board(config.getBoardWidth(), config.getBoardHeight(), defender);
        Ship ship = new Ship(ShipType.DESTROYER);
        board.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        UUID boardId = UUID.randomUUID();
        setId(board, boardId);
        game.addBoard(board);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act
        BoardStateDto state = gameService.getBoardState(code, boardId);

        // Assert
        assertThat(state.boardId()).isEqualTo(boardId);
        assertThat(state.width()).isEqualTo(config.getBoardWidth());
        assertThat(state.height()).isEqualTo(config.getBoardHeight());
        assertThat(state.locked()).isFalse();

        assertThat(state.shipPlacements()).hasSize(1);
        var sp = state.shipPlacements().get(0);
        assertThat(sp.type()).isEqualTo(ShipType.DESTROYER);
        assertThat(sp.startX()).isEqualTo(3);
        assertThat(sp.startY()).isEqualTo(3);
        assertThat(sp.orientation()).isEqualTo(Orientation.HORIZONTAL);
    }

    @Test
    void getBoardState_shouldThrowEntityNotFound_whenGameDoesNotExist() {
        // Arrange
        String code = "UNKNOWN";
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.empty());

        UUID someBoardId = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> gameService.getBoardState(code, someBoardId))
                .isInstanceOf(EntityNotFoundException.class);

        verify(gameRepository, times(1)).findByGameCode(code);
        verifyNoMoreInteractions(gameRepository);
    }

    @Test
    void getBoardState_shouldThrowIllegalState_whenBoardDoesNotBelongToGame() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        Player player = new Player("Player1");
        game.addPlayer(player);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        UUID unknownBoardId = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> gameService.getBoardState(code, unknownBoardId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Board does not belong");

        verify(gameRepository, times(1)).findByGameCode(code);
        verifyNoMoreInteractions(gameRepository);
    }

    @Test
    void getBoardAscii_shouldRenderShipsAndShotsWithSymbols_whenShowShipsIsTrue() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        Board defenderBoard = new Board(config.getBoardWidth(), config.getBoardHeight(), defender);

        // DESTROYER at (3,3) horizontal -> (3,3) and (4,3)
        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        // shots via domain logic (not the service)
        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3)); // HIT
        game.fireShot(attacker, defenderBoard, new Coordinate(0, 0)); // MISS

        UUID boardId = UUID.randomUUID();
        setId(defenderBoard, boardId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act
        String ascii = gameService.getBoardAscii(code, boardId, true);

        // Assert
        assertThat(ascii).contains("Board (" + config.getBoardWidth() + "x" + config.getBoardHeight() + ")");

        String[] lines = ascii.split("\\R");

        // Layout assumptions:
        // 0: "Board (10x10)"
        // 1: "   0 1 2 3 ..."
        // 2: " 0 . . . ..."
        // row y=n -> lines[2+n]
        // col x -> char index 3 + x*2
        String row0 = lines[2];
        assertThat(row0.charAt(3)).isEqualTo('O'); // (0,0) = MISS -> O

        String row3 = lines[2 + 3];
        assertThat(row3.charAt(3 + 3 * 2)).isEqualTo('X'); // (3,3) = HIT -> X
        assertThat(row3.charAt(3 + 4 * 2)).isEqualTo('S'); // (4,3) = ship shown -> S
    }

    @Test
    void getBoardAscii_shouldHideShipsWhenShowShipsIsFalse() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        Board defenderBoard = new Board(config.getBoardWidth(), config.getBoardHeight(), defender);

        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3)); // HIT
        game.fireShot(attacker, defenderBoard, new Coordinate(0, 0)); // MISS

        UUID boardId = UUID.randomUUID();
        setId(defenderBoard, boardId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act
        String ascii = gameService.getBoardAscii(code, boardId, false);

        // Assert
        String[] lines = ascii.split("\\R");

        String row0 = lines[2];
        assertThat(row0.charAt(3)).isEqualTo('O'); // (0,0) MISS

        String row3 = lines[2 + 3];
        assertThat(row3.charAt(3 + 3 * 2)).isEqualTo('X'); // (3,3) HIT
        assertThat(row3.charAt(3 + 4 * 2)).isEqualTo('.'); // (4,3) ship hidden -> .
    }

    // ------------------------------------------------------------------------------------
    // Debug helper (optional)
    // ------------------------------------------------------------------------------------

    /**
     * Debug-only helper to print a board with ships as 'S' to console.
     * Not used by assertions; useful when diagnosing failing placement tests.
     */
    private void printBoard(Board board) {
        int width = board.getWidth();
        int height = board.getHeight();

        char[][] grid = new char[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = '.';
            }
        }

        for (ShipPlacement placement : board.getPlacements()) {
            for (Coordinate c : placement.getCoveredCoordinates()) {
                grid[c.getY()][c.getX()] = 'S';
            }
        }

        System.out.println("Board (" + width + "x" + height + "):");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                System.out.print(grid[y][x] + " ");
            }
            System.out.println();
        }
    }
}
