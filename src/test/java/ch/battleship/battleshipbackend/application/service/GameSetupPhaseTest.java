package ch.battleship.battleshipbackend.application.service;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;

import java.util.Optional;
import java.util.UUID;

import ch.battleship.battleshipbackend.web.api.dto.GamePublicDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        // findByGameCode wird in allen Tests gebraucht (rerollBoard / confirmBoard / getBoardState)
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));

        // save(...) wird NICHT in allen Tests gebraucht (z.B. locked -> Exception vor save)
        // -> deshalb lenient, damit Mockito strict nicht meckert
        lenient().when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------------------------
    // rerollBoard
    // ------------------------------------------------------------------------------------

    @Test
    void rerollBoard_shouldPlaceFleet_andReturnBoardState() {
        BoardStateDto dto = gameService.rerollBoard(GAME_CODE, playerA.getId());

        assertNotNull(dto);
        assertEquals(boardA.getId(), dto.boardId());

        // 2x2 + 2x3 + 1x4 + 1x5 => 6 Schiffe, total 19 Zellen
        assertEquals(6, dto.shipPlacements().size(), "Expected ship count to match fleetDefinition");
        int totalCells = dto.shipPlacements().stream().mapToInt(s -> s.size()).sum();
        assertEquals(19, totalCells, "Expected total ship cells to match fleetDefinition");
    }

    @Test
    void rerollBoard_shouldThrowIllegalState_whenBoardLocked() {
        boardA.setLocked(true);
        assertThrows(IllegalStateException.class,
                () -> gameService.rerollBoard(GAME_CODE, playerA.getId()));
    }

    @Test
    void rerollBoard_shouldThrowIllegalState_whenGameNotInSetup() {
        game.setStatus(GameStatus.RUNNING);
        assertThrows(IllegalStateException.class,
                () -> gameService.rerollBoard(GAME_CODE, playerA.getId()));
    }

    // ------------------------------------------------------------------------------------
    // confirmBoard
    // ------------------------------------------------------------------------------------

    @Test
    void confirmBoard_shouldLockBoard_andStayInSetup_whenOnlyOnePlayerConfirmed() {

        gameService.rerollBoard(GAME_CODE, playerA.getId());

        gameService.confirmBoard(GAME_CODE, playerA.getId());

        assertTrue(boardA.isLocked());
        assertFalse(boardB.isLocked());

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository, atLeastOnce()).save(gameCaptor.capture());
        Game persisted = gameCaptor.getValue();

        assertEquals(GameStatus.SETUP, persisted.getStatus());
    }

    @Test
    void confirmBoard_shouldSwitchToRunning_whenBothPlayersConfirmed() {
        gameService.rerollBoard(GAME_CODE, playerA.getId());
        gameService.rerollBoard(GAME_CODE, playerB.getId());

        gameService.confirmBoard(GAME_CODE, playerA.getId());
        GamePublicDto dto = gameService.confirmBoard(GAME_CODE, playerB.getId());

        assertTrue(boardA.isLocked());
        assertTrue(boardB.isLocked());
        assertEquals(GameStatus.RUNNING, dto.status(),
                "Game should become RUNNING after both players confirmed");

        // Optional: DTO Sicht von Player B (sollte nach 2nd confirm beide boards locked sehen)
        assertTrue(dto.yourBoardLocked());
        assertTrue(dto.opponentBoardLocked());
    }
}
