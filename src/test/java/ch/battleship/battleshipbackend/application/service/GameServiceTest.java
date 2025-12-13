package ch.battleship.battleshipbackend.application.service;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;
import ch.battleship.battleshipbackend.web.api.dto.ShipPlacementDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


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

        // Assert: Rückgabewert
        assertThat(game).isNotNull();
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(game.getGameCode()).isNotBlank();

        // Assert: Interaktion mit Repository
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
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config); // status = WAITING, players/boards = empty

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Game result = gameService.joinGame(code, "Player1");

        // Assert
        assertThat(result.getPlayers()).hasSize(1);
        Player p1 = result.getPlayers().get(0);
        assertThat(p1.getUsername()).isEqualTo("Player1");

        assertThat(result.getBoards()).hasSize(1);
        Board b1 = result.getBoards().get(0);
        assertThat(b1.getOwner()).isEqualTo(p1);
        assertThat(b1.getWidth()).isEqualTo(10);
        assertThat(b1.getHeight()).isEqualTo(10);

        assertThat(result.getStatus()).isEqualTo(GameStatus.WAITING);
        verify(gameRepository, times(1)).save(game);
    }

    @Test
    void joinGame_secondPlayer_shouldSetStatusSetup_andCreateSecondBoard() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        // Konsistenter Startzustand: 1 Player + 1 Board
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
        assertThat(result.getBoards())
                .anyMatch(b -> b.getOwner().equals(p2));

        assertThat(result.getStatus()).isEqualTo(GameStatus.SETUP);
        verify(gameRepository, times(1)).save(game);
    }

    @Test
    void joinGame_thirdPlayer_shouldThrowIllegalStateException_andNotChangeBoards() {
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

        assertThat(game.getPlayers()).hasSize(2);
        assertThat(game.getBoards()).hasSize(2);
        verify(gameRepository, never()).save(any());
    }

    @Test
    void joinGame_nonExistingGame_shouldThrowEntityNotFound() {
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
        Game game = new Game(code, config); // status = WAITING, noch keine Player/Boards

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Game result = gameService.joinGame(code, "Player1");

        // Assert
        assertThat(result.getBoards()).hasSize(1);
        Board board = result.getBoards().get(0);

        // 1) Es sollten Schiffe platziert sein
        assertThat(board.getPlacements())
                .as("Es sollten ShipPlacements auf dem Board existieren")
                .isNotEmpty();

        // 2) Für die Default-Config: "2x2,2x3,1x4,1x5"
        //    => 2x DESTROYER, 2x CRUISER, 1x BATTLESHIP, 1x CARRIER
        var types = board.getPlacements().stream()
                .map(p -> p.getShip().getType())
                .toList();

        assertThat(types)
                .containsExactlyInAnyOrder(
                        ShipType.DESTROYER, ShipType.DESTROYER,
                        ShipType.CRUISER,   ShipType.CRUISER,
                        ShipType.BATTLESHIP,
                        ShipType.CARRIER
                );

        // 3) Alle Koordinaten müssen innerhalb des Boards liegen und dürfen sich nicht überlappen
        var allCoords = board.getPlacements().stream()
                .flatMap(p -> p.getCoveredCoordinates().stream())
                .toList();

        // Board-Grenzen
        assertThat(allCoords)
                .allSatisfy(c -> {
                    assertThat(c.getX()).isBetween(0, board.getWidth() - 1);
                    assertThat(c.getY()).isBetween(0, board.getHeight() - 1);
                });

        // Keine doppelten Koordinaten (keine Überlappung)
        assertThat(allCoords)
                .as("Kein Feld darf von zwei verschiedenen Schiffen belegt werden")
                .doesNotHaveDuplicates();

        // Debug-Ausgabe:
        // printBoard(board);
    }

    // ------------------------------------------------------------------------------------
    // fireShot (Service-Logik, inkl. Validierung + Repo)
    // ------------------------------------------------------------------------------------

    @Test
    void fireShot_shouldReturnShotAndPersistGame_whenAllDataIsValid() {
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

        Ship ship = new Ship(ShipType.DESTROYER); // size 2
        defenderBoard.placeShip(
                ship,
                new Coordinate(3, 3),
                Orientation.HORIZONTAL
        );

        game.addBoard(defenderBoard);

        // >>> IDs simulieren (persistierter Zustand)
        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        setId(attacker, attackerId);
        setId(defender, defenderId);
        setId(defenderBoard, boardId);

        // --- Stubbing ---
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // ShotRepository: gebe einfach das gleiche Shot-Objekt zurück,
        // das reinkommt (wie eine "Fake-DB")
        when(shotRepository.save(any(Shot.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Shot shot = gameService.fireShot(
                code,
                attackerId,
                boardId,
                3,
                3
        );

        // Assert
        assertThat(shot).isNotNull();
        assertThat(shot.getResult()).isEqualTo(ShotResult.HIT);
        assertThat(shot.getShooter()).isEqualTo(attacker);
        assertThat(shot.getTargetBoard()).isEqualTo(defenderBoard);
        assertThat(game.getShots()).hasSize(1);

        // Interaktionen prüfen
        verify(gameRepository, times(1)).findByGameCode(code);
        verify(gameRepository, times(1)).save(game);
        verify(shotRepository, times(1)).save(any(Shot.class));
        verifyNoMoreInteractions(gameRepository, shotRepository);
    }


    @Test
    void fireShot_shouldThrowEntityNotFound_whenGameDoesNotExist() {
        String code = "UNKNOWN";
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                gameService.fireShot(code, UUID.randomUUID(), UUID.randomUUID(), 0, 0)
        ).isInstanceOf(EntityNotFoundException.class);

        verify(gameRepository, never()).save(any());
    }

    @Test
    void fireShot_shouldThrowIllegalState_whenGameIsNotRunning() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config); // Status = WAITING
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        assertThatThrownBy(() ->
                gameService.fireShot(code, UUID.randomUUID(), UUID.randomUUID(), 0, 0)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not RUNNING");

        verify(gameRepository, never()).save(any());
    }

    @Test
    void fireShot_shouldThrowIllegalState_whenShooterIsNotPartOfGame() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player somePlayer = new Player("SomePlayer");
        game.addPlayer(somePlayer);

        Board board = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                somePlayer
        );
        game.addBoard(board);

        UUID unknownShooterId = UUID.randomUUID();

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        assertThatThrownBy(() ->
                gameService.fireShot(code, unknownShooterId, board.getId(), 0, 0)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Shooter does not belong");

        verify(gameRepository, never()).save(any());
    }

    @Test
    void fireShot_shouldThrowIllegalState_whenShootingOwnBoard() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player attacker = new Player("Attacker");
        game.addPlayer(attacker);

        Board ownBoard = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                attacker
        );
        game.addBoard(ownBoard);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        assertThatThrownBy(() ->
                gameService.fireShot(code, attacker.getId(), ownBoard.getId(), 0, 0)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot shoot at own board");

        verify(gameRepository, never()).save(any());
    }

    @Test
    void fireShot_shouldThrowIllegalArgument_whenCoordinateOutOfBounds() {
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
        game.addBoard(defenderBoard);

        // >>> IDs simulieren (persistierter Zustand)
        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        setId(attacker, attackerId);
        setId(defender, defenderId);
        setId(defenderBoard, boardId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        int xOutOfBounds = defenderBoard.getWidth();   // gültig: 0..width-1
        int yOutOfBounds = defenderBoard.getHeight();  // gültig: 0..height-1

        // Act + Assert
        assertThatThrownBy(() ->
                gameService.fireShot(code, attackerId, boardId, xOutOfBounds, yOutOfBounds)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of board bounds");

        verify(gameRepository, never()).save(any());
    }

    @Test
    void getBoardState_shouldReturnBoardWithShipsAndShots() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        Board defenderBoard = new Board(
                config.getBoardWidth(),
                config.getBoardHeight(),
                defender
        );

        // Ein Schiff platzieren: DESTROYER bei (3,3) horizontal
        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        // Zwei Schüsse auf dieses Board (direkt über Domain, ohne Service)
        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3)); // HIT
        game.fireShot(attacker, defenderBoard, new Coordinate(0, 0)); // MISS

        // IDs simulieren
        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        setId(attacker, attackerId);
        setId(defender, defenderId);
        setId(defenderBoard, boardId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act
        BoardStateDto state = gameService.getBoardState(code, boardId);

        // Assert
        assertThat(state.boardId()).isEqualTo(boardId);
        assertThat(state.width()).isEqualTo(config.getBoardWidth());
        assertThat(state.height()).isEqualTo(config.getBoardHeight());
        assertThat(state.ownerId()).isEqualTo(defenderId);
        assertThat(state.ownerUsername()).isEqualTo("Defender");

        // Schiffe
        assertThat(state.ships()).hasSize(1);
        ShipPlacementDto sp = state.ships().get(0);
        assertThat(sp.type()).isEqualTo(ShipType.DESTROYER);
        assertThat(sp.startX()).isEqualTo(3);
        assertThat(sp.startY()).isEqualTo(3);
        assertThat(sp.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(sp.size()).isEqualTo(2);

        // Shots auf dieses Board
        assertThat(state.shotsOnThisBoard()).hasSize(2);
        assertThat(state.shotsOnThisBoard())
                .anySatisfy(s -> {
                    assertThat(s.x()).isEqualTo(3);
                    assertThat(s.y()).isEqualTo(3);
                    assertThat(s.result()).isEqualTo(ShotResult.HIT);
                })
                .anySatisfy(s -> {
                    assertThat(s.x()).isEqualTo(0);
                    assertThat(s.y()).isEqualTo(0);
                    assertThat(s.result()).isEqualTo(ShotResult.MISS);
                });

        // Kein save(), da getBoardState nur liest
        verify(gameRepository, times(1)).findByGameCode(code);
        verifyNoMoreInteractions(gameRepository);
    }

    @Test
    void getBoardState_shouldThrowEntityNotFound_whenGameDoesNotExist() {
        // Arrange
        String code = "UNKNOWN";
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.empty());

        UUID someBoardId = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() ->
                gameService.getBoardState(code, someBoardId)
        ).isInstanceOf(EntityNotFoundException.class);

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

        // Game hat KEIN Board mit der Id, die wir gleich abfragen
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        UUID unknownBoardId = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() ->
                gameService.getBoardState(code, unknownBoardId)
        ).isInstanceOf(IllegalStateException.class)
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

        // Schiff: DESTROYER bei (3,3) horizontal -> Felder (3,3) und (4,3)
        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        // Zwei Schüsse auf dieses Board (direkt über Domain)
        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3)); // HIT
        game.fireShot(attacker, defenderBoard, new Coordinate(0, 0)); // MISS

        // IDs simulieren
        UUID boardId = UUID.randomUUID();
        setId(defenderBoard, boardId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act
        String ascii = gameService.getBoardAscii(code, boardId, true);
        System.out.println(ascii);
        // Assert: Header
        assertThat(ascii)
                .contains("Board (" + config.getBoardWidth() + "x" + config.getBoardHeight() + ")");

        String[] lines = ascii.split("\\R");

        // Layout:
        // 0: "Board (10x10)"
        // 1: "   0 1 2 3 4 5 6 7 8 9 "
        // 2: " 0 . . . . . ..."
        // => Zeile für y = n: index 2 + n
        //    Spalte x: char an Position 3 + x*2

        // Zeile y = 0
        String row0 = lines[2];
        // (0,0) = Miss -> 'O'
        assertThat(row0.charAt(3 + 0 * 2)).isEqualTo('O');

        // Zeile y = 3
        String row3 = lines[2 + 3];
        // (3,3) = Hit -> 'X'
        assertThat(row3.charAt(3 + 3 * 2)).isEqualTo('X');
        // (4,3) = ungetroffenes Schiff -> 'S'
        assertThat(row3.charAt(3 + 4 * 2)).isEqualTo('S');

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

        // Schiff: DESTROYER bei (3,3) horizontal
        Ship ship = new Ship(ShipType.DESTROYER);
        defenderBoard.placeShip(ship, new Coordinate(3, 3), Orientation.HORIZONTAL);

        game.addBoard(defenderBoard);

        // Shots
        game.fireShot(attacker, defenderBoard, new Coordinate(3, 3)); // HIT
        game.fireShot(attacker, defenderBoard, new Coordinate(0, 0)); // MISS

        UUID boardId = UUID.randomUUID();
        setId(defenderBoard, boardId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));

        // Act
        String ascii = gameService.getBoardAscii(code, boardId, false);
        System.out.println(ascii);

        // Assert
        String[] lines = ascii.split("\\R");

        // Zeile y = 0
        String row0 = lines[2];
        // (0,0) = Miss -> 'O'
        assertThat(row0.charAt(3 + 0 * 2)).isEqualTo('O');

        // Zeile y = 3
        String row3 = lines[2 + 3];
        // (3,3) = Hit -> 'X'
        assertThat(row3.charAt(3 + 3 * 2)).isEqualTo('X');
        // (4,3) = ungetroffenes Schiff, aber showShips=false -> '.'
        assertThat(row3.charAt(3 + 4 * 2)).isEqualTo('.');

    }


    private void printBoard(Board board) {
        int width = board.getWidth();
        int height = board.getHeight();

        char[][] grid = new char[height][width];

        // alles zuerst mit '.' füllen
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = '.';
            }
        }

        // Schiffe einzeichnen
        for (ShipPlacement placement : board.getPlacements()) {
            for (Coordinate c : placement.getCoveredCoordinates()) {
                int x = c.getX();
                int y = c.getY();
                // einfache Darstellung: S = Ship
                grid[y][x] = 'S';
            }
        }

        // Ausgabe in die Konsole (Test-Output)
        System.out.println("Board (" + width + "x" + height + "):");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                System.out.print(grid[y][x] + " ");
            }
            System.out.println();
        }
    }
}