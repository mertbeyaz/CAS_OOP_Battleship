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

        makeBoardReady(boardA, config);
        makeBoardReady(boardB, config);

        game.addBoard(boardA);
        game.addBoard(boardB);

        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        lenient().when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }


    @Test
    void confirmBoard_shouldSendBoardConfirmedEvent_toGameEventsTopic() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);

        // Assert
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.BOARD_CONFIRMED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.SETUP, evt.gameStatus());

        assertEquals(PLAYER_A_ID.toString(), evt.payload().get("playerId"));
        assertEquals("PlayerA", evt.payload().get("playerName"));
    }

    @Test
    void confirmBoard_shouldSetRunningAndSendGameStartedEvent_whenAllBoardsLocked() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);
        Game updated = gameService.confirmBoard(GAME_CODE, PLAYER_B_ID);

        // Assert: game started
        assertEquals(GameStatus.RUNNING, updated.getStatus());
        assertNotNull(updated.getCurrentTurnPlayerId());

        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate, times(3))
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        assertEquals(GameEventType.BOARD_CONFIRMED, captor.getAllValues().get(0).type());
        assertEquals(GameEventType.BOARD_CONFIRMED, captor.getAllValues().get(1).type());
        assertEquals(GameEventType.GAME_STARTED, captor.getAllValues().get(2).type());

        GameEventDto started = captor.getAllValues().get(2);
        assertEquals(updated.getCurrentTurnPlayerId().toString(), started.payload().get("currentTurnPlayerId"));
        assertNotNull(started.payload().get("currentTurnPlayerName"));
    }

    @Test
    void confirmBoard_shouldSendBoardConfirmedEvent_withRunningStatus_whenSecondPlayerConfirms() {
        // Act
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);
        Game savedAfterSecond = gameService.confirmBoard(GAME_CODE, PLAYER_B_ID);

        // Assert
        assertEquals(GameStatus.RUNNING, savedAfterSecond.getStatus());

        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate, times(3))
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        // Events: [0]=BOARD_CONFIRMED(A), [1]=BOARD_CONFIRMED(B), [2]=GAME_STARTED
        GameEventDto secondConfirm = captor.getAllValues().get(1);
        assertEquals(GameEventType.BOARD_CONFIRMED, secondConfirm.type());
        assertEquals(GameStatus.RUNNING, secondConfirm.gameStatus());
        assertEquals(PLAYER_B_ID.toString(), secondConfirm.payload().get("playerId"));

        GameEventDto started = captor.getAllValues().get(2);
        assertEquals(GameEventType.GAME_STARTED, started.type());
        assertNotNull(started.payload().get("currentTurnPlayerId"));
    }

    @Test
    void confirmBoard_shouldNotSendEvent_whenGameNotFound() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> gameService.confirmBoard(GAME_CODE, PLAYER_A_ID));

        verifyNoInteractions(messagingTemplate);
    }


    private static void makeBoardReady(Board board, GameConfiguration config) {
        int expectedShips = expectedShipsFromFleetDefinition(config.getFleetDefinition());

        board.getPlacements().clear();
        for (int i = 0; i < expectedShips; i++) {
            board.getPlacements().add(null); // confirmBoard prüft nur size
        }
    }

    private static int expectedShipsFromFleetDefinition(String fleetDefinition) {
        if (fleetDefinition == null || fleetDefinition.isBlank()) {
            throw new IllegalStateException("Fleet definition is missing");
        }

        int sum = 0;
        String[] parts = fleetDefinition.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;

            // format: "<count>x<size>" e.g. "2x3"
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