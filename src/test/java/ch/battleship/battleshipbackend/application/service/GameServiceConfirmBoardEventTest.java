package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.GameEventDto;
import ch.battleship.battleshipbackend.web.api.dto.GamePublicDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GameService#confirmBoard(String, UUID)} with focus on WebSocket events.
 *
 * <p>Focus:
 * <ul>
 *   <li>BOARD_CONFIRMED event is sent when a player confirms their board</li>
 *   <li>When both boards are locked, the game becomes RUNNING and GAME_STARTED is sent</li>
 *   <li>Event payload must not contain internal identifiers (anti-cheat / API contract)</li>
 * </ul>
 *
 * <p>Note:
 * These tests mock persistence and messaging. The domain state transitions are validated
 * through returned DTOs and captured events.
 */
@ExtendWith(MockitoExtension.class)
class GameServiceConfirmBoardEventTest {

    private static final String GAME_CODE = "TEST-GAME";
    private static final UUID PLAYER_A_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ShotRepository shotRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    private Game game;
    private Player playerA;
    private Player playerB;

    @BeforeEach
    void setUp() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        game = new Game(GAME_CODE, config);
        game.setStatus(GameStatus.SETUP);

        playerA = new Player("PlayerA");
        playerB = new Player("PlayerB");
        setId(playerA, PLAYER_A_ID);
        setId(playerB, PLAYER_B_ID);

        game.addPlayer(playerA);
        game.addPlayer(playerB);

        Board boardA = new Board(config.getBoardWidth(), config.getBoardHeight(), playerA);
        Board boardB = new Board(config.getBoardWidth(), config.getBoardHeight(), playerB);

        // confirmBoard checks only the number of placements (not their content).
        // For unit tests we simulate a "ready" board by setting the correct list size.
        makeBoardReady(boardA, config);
        makeBoardReady(boardB, config);

        game.addBoard(boardA);
        game.addBoard(boardB);

        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        // Return the passed aggregate to keep the test purely in-memory.
        lenient().when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void confirmBoard_shouldSendBoardConfirmedEvent_toGameEventsTopic() {
        // Act
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);

        // Assert: one BOARD_CONFIRMED event was broadcast
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.BOARD_CONFIRMED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.SETUP, evt.gameStatus());

        // Anti-cheat: payload should not leak internal ids
        assertFalse(evt.payload().containsKey("playerId"));
        assertEquals("PlayerA", evt.payload().get("playerName"));
    }

    @Test
    void confirmBoard_shouldSetRunningAndSendGameStartedEvent_whenAllBoardsLocked() {
        // Act
        GamePublicDto dtoA = gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);
        GamePublicDto dtoB = gameService.confirmBoard(GAME_CODE, PLAYER_B_ID);

        // Assert: game started (DTO view of player B)
        assertEquals(GameStatus.RUNNING, dtoB.status());
        assertTrue(dtoB.yourBoardLocked());
        assertTrue(dtoB.opponentBoardLocked());

        // Events: 2x BOARD_CONFIRMED + 1x GAME_STARTED
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate, times(3))
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        assertEquals(GameEventType.BOARD_CONFIRMED, captor.getAllValues().get(0).type());
        assertEquals(GameEventType.BOARD_CONFIRMED, captor.getAllValues().get(1).type());
        assertEquals(GameEventType.GAME_STARTED, captor.getAllValues().get(2).type());

        // GAME_STARTED payload must not contain ids
        GameEventDto started = captor.getAllValues().get(2);
        assertFalse(started.payload().containsKey("currentTurnPlayerId"));
    }

    @Test
    void confirmBoard_shouldAssignFirstTurnPlayerId_whenGameStarts() {
        // Act
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);
        gameService.confirmBoard(GAME_CODE, PLAYER_B_ID);

        // Assert: service persists a RUNNING game with assigned current turn player
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository, atLeastOnce()).save(gameCaptor.capture());

        Game persisted = gameCaptor.getValue();
        assertEquals(GameStatus.RUNNING, persisted.getStatus());
        assertNotNull(persisted.getCurrentTurnPlayerId());
    }

    @Test
    void confirmBoard_shouldSendBoardConfirmedEvent_withRunningStatus_whenSecondPlayerConfirms() {
        // Act
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);
        GamePublicDto dtoAfterSecond = gameService.confirmBoard(GAME_CODE, PLAYER_B_ID);

        // Assert: after second confirmation the game is RUNNING
        assertEquals(GameStatus.RUNNING, dtoAfterSecond.status());

        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate, times(3))
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        // Events: [0]=BOARD_CONFIRMED(A), [1]=BOARD_CONFIRMED(B), [2]=GAME_STARTED
        GameEventDto secondConfirm = captor.getAllValues().get(1);
        assertEquals(GameEventType.BOARD_CONFIRMED, secondConfirm.type());
        assertEquals(GameStatus.RUNNING, secondConfirm.gameStatus());

        // Anti-cheat: no ids in WS payload
        assertFalse(secondConfirm.payload().containsKey("playerId"));

        GameEventDto started = captor.getAllValues().get(2);
        assertEquals(GameEventType.GAME_STARTED, started.type());
        assertFalse(started.payload().containsKey("currentTurnPlayerId"));
    }

    @Test
    void confirmBoard_shouldNotSendEvent_whenGameNotFound() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(EntityNotFoundException.class,
                () -> gameService.confirmBoard(GAME_CODE, PLAYER_A_ID));

        verifyNoInteractions(messagingTemplate);
    }

    /**
     * Simulates a board that is "ready" for confirmation.
     *
     * <p>Implementation note:
     * {@code confirmBoard(...)} currently checks only the number of placements, not their contents.
     * To keep this test focused on event publishing, we populate the placement list with {@code null}
     * entries to reach the expected size without running the full fleet-placement algorithm.
     */
    private static void makeBoardReady(Board board, GameConfiguration config) {
        int expectedShips = expectedShipsFromFleetDefinition(config.getFleetDefinition());

        board.getPlacements().clear();
        for (int i = 0; i < expectedShips; i++) {
            board.getPlacements().add(null);
        }
    }

    /**
     * Parses the fleet definition string (e.g. {@code "2x2,2x3,1x4,1x5"}) and returns the ship count.
     *
     * @param fleetDefinition string in "<count>x<size>" format, comma-separated
     * @return total number of ships defined
     * @throws IllegalStateException if the format is missing or invalid
     */
    private static int expectedShipsFromFleetDefinition(String fleetDefinition) {
        if (fleetDefinition == null || fleetDefinition.isBlank()) {
            throw new IllegalStateException("Fleet definition is missing");
        }

        int sum = 0;
        String[] parts = fleetDefinition.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;

            int xPos = token.indexOf('x');
            if (xPos <= 0) {
                throw new IllegalStateException("Invalid fleet token: " + token);
            }

            String countStr = token.substring(0, xPos).trim();
            int count = Integer.parseInt(countStr);
            sum += count;
        }
        return sum;
    }
}
