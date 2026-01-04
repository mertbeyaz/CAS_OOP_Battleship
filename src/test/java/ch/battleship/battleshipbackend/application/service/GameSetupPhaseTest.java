package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;
import ch.battleship.battleshipbackend.web.api.dto.GamePublicDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the <b>SETUP phase</b> of the Battleship game.
 *
 * <p>This test class focuses on the flow that happens after both players have joined:
 * <ul>
 *   <li>{@link GameService#rerollBoard(String, UUID)}: auto-place fleet (randomized) while board is not locked</li>
 *   <li>{@link GameService#confirmBoard(String, UUID)}: lock board; when both boards are locked -> game becomes RUNNING</li>
 * </ul>
 *
 * <p>We keep the tests small and scenario-based:
 * each test validates one rule of the setup phase (state transitions, locking rules, and DTO outcomes).
 */
@ExtendWith(MockitoExtension.class)
class GameSetupPhaseTest {

    private static final String GAME_CODE = "TEST-GAME-CODE";

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ShotRepository shotRepository;

    @InjectMocks
    private GameService gameService;

    private Game game;
    private Player playerA;
    private Player playerB;
    private Board boardA;
    private Board boardB;

    /**
     * Creates a deterministic baseline:
     * <ul>
     *   <li>Game in SETUP</li>
     *   <li>Two players with fixed UUIDs</li>
     *   <li>Two boards with fixed UUIDs</li>
     *   <li>Repository returns this game for {@code findByGameCode}</li>
     * </ul>
     *
     * <p>{@code save(...)} is configured as lenient because not every test reaches persistence
     * (e.g. exceptions are thrown before save is called). This avoids Mockito strict stubbing issues.
     */
    @BeforeEach
    void setUp() {
        GameConfiguration config = GameConfiguration.defaultConfig();

        game = new Game(GAME_CODE, config);
        game.setStatus(GameStatus.SETUP);

        playerA = new Player("PlayerA");
        playerB = new Player("PlayerB");

        setId(playerA, UUID.fromString("20000000-0000-0000-0000-000000000001"));
        setId(playerB, UUID.fromString("20000000-0000-0000-0000-000000000002"));

        game.addPlayer(playerA);
        game.addPlayer(playerB);

        boardA = new Board(config.getBoardWidth(), config.getBoardHeight(), playerA);
        boardB = new Board(config.getBoardWidth(), config.getBoardHeight(), playerB);

        setId(boardA, UUID.fromString("10000000-0000-0000-0000-000000000001"));
        setId(boardB, UUID.fromString("10000000-0000-0000-0000-000000000002"));

        game.addBoard(boardA);
        game.addBoard(boardB);

        // Used by all setup-phase service methods in this test class
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));

        // Not every test calls save(...) -> keep it lenient to avoid UnnecessaryStubbingException
        lenient().when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------------------------
    // rerollBoard
    // ------------------------------------------------------------------------------------

    /**
     * Reroll should auto-place a complete fleet and return a BoardStateDto.
     *
     * <p>We assert the fleet definition of the default config:
     * {@code 2x2,2x3,1x4,1x5} -> 6 ships total with 19 occupied cells.
     */
    @Test
    void rerollBoard_shouldPlaceFleet_andReturnBoardState() {
        // Act
        BoardStateDto dto = gameService.rerollBoard(GAME_CODE, playerA.getId());

        // Assert
        assertNotNull(dto, "BoardStateDto must not be null");
        assertEquals(boardA.getId(), dto.boardId(), "DTO must reference the correct board");

        // Default fleet: 2x2 + 2x3 + 1x4 + 1x5 => 6 ships, 19 total cells
        assertEquals(6, dto.shipPlacements().size(), "Ship count must match fleetDefinition");
        int totalCells = dto.shipPlacements().stream().mapToInt(s -> s.size()).sum();
        assertEquals(19, totalCells, "Total occupied cells must match fleetDefinition");
    }

    /**
     * Locked boards are immutable in SETUP: rerolling must be rejected.
     */
    @Test
    void rerollBoard_shouldThrowIllegalState_whenBoardLocked() {
        // Arrange
        boardA.setLocked(true);

        // Act + Assert
        assertThrows(IllegalStateException.class,
                () -> gameService.rerollBoard(GAME_CODE, playerA.getId()),
                "Reroll must fail when board is locked");
    }

    /**
     * Reroll is only allowed in SETUP. Any other game status must be rejected.
     */
    @Test
    void rerollBoard_shouldThrowIllegalState_whenGameNotInSetup() {
        // Arrange
        game.setStatus(GameStatus.RUNNING);

        // Act + Assert
        assertThrows(IllegalStateException.class,
                () -> gameService.rerollBoard(GAME_CODE, playerA.getId()),
                "Reroll must fail when game is not in SETUP");
    }

    // ------------------------------------------------------------------------------------
    // confirmBoard
    // ------------------------------------------------------------------------------------

    /**
     * Confirming a board locks the player's board.
     * If only one player confirmed, the game must remain in SETUP.
     *
     * <p>We call {@code rerollBoard} first to ensure the board is "ready" (placements exist).
     */
    @Test
    void confirmBoard_shouldLockBoard_andStayInSetup_whenOnlyOnePlayerConfirmed() {
        // Arrange: make PlayerA board ready
        gameService.rerollBoard(GAME_CODE, playerA.getId());

        // Act
        gameService.confirmBoard(GAME_CODE, playerA.getId());

        // Assert: lock states
        assertTrue(boardA.isLocked(), "PlayerA board must be locked after confirm");
        assertFalse(boardB.isLocked(), "PlayerB board must still be unlocked");

        // Assert: game status is still SETUP
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository, atLeastOnce()).save(gameCaptor.capture());
        Game persisted = gameCaptor.getValue();
        assertEquals(GameStatus.SETUP, persisted.getStatus(), "Game must remain in SETUP after first confirmation");
    }

    /**
     * When both players confirm their boards, the game transitions to RUNNING.
     *
     * <p>We also verify the public DTO from the perspective of the second confirmer:
     * both boards should be reported as locked.
     */
    @Test
    void confirmBoard_shouldSwitchToRunning_whenBothPlayersConfirmed() {
        // Arrange: make both boards ready
        gameService.rerollBoard(GAME_CODE, playerA.getId());
        gameService.rerollBoard(GAME_CODE, playerB.getId());

        // Act: confirm both boards
        gameService.confirmBoard(GAME_CODE, playerA.getId());
        GamePublicDto dto = gameService.confirmBoard(GAME_CODE, playerB.getId());

        // Assert: lock states
        assertTrue(boardA.isLocked(), "PlayerA board must be locked");
        assertTrue(boardB.isLocked(), "PlayerB board must be locked");

        // Assert: status transition
        assertEquals(GameStatus.RUNNING, dto.status(),
                "Game should become RUNNING after both players confirmed");

        // Assert: public state from PlayerB perspective
        assertTrue(dto.yourBoardLocked(), "Second confirmer must see their own board locked");
        assertTrue(dto.opponentBoardLocked(), "Second confirmer must see opponent board locked");
    }
}
