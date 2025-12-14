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

        // Falls Boards nicht automatisch erstellt werden, müsst ihr sie hier adden
        // (bei euch gibt es game.getBoards() -> also existiert die Beziehung definitiv).
        // Wenn ihr Boards bereits beim Game-Create erstellt: diesen Block weglassen.
        Board boardA = new Board(config.getBoardWidth(), config.getBoardHeight(), playerA);
        Board boardB = new Board(config.getBoardWidth(), config.getBoardHeight(), playerB);
        game.addBoard(boardA);
        game.addBoard(boardB);

        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        lenient().when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void confirmBoard_shouldSendBoardConfirmedEvent_toGameEventsTopic() {
        // Arrange: Board A "ready" machen, damit confirmBoard nicht an placements scheitert
        // -> wir nutzen euren eigenen Generator / rerollBoard
        gameService.rerollBoard(GAME_CODE, PLAYER_A_ID);

        // Act
        Game saved = gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);

        assertEquals(GameStatus.SETUP, saved.getStatus());

        // Assert: STOMP Event wurde gesendet
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertNotNull(evt);

        assertEquals(GameEventType.BOARD_CONFIRMED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(PLAYER_A_ID.toString(), evt.playerId());
        assertEquals("PlayerA", evt.playerName());

        assertEquals(GameStatus.SETUP, evt.gameStatus());

        assertNotNull(evt.timeStamp());
    }

    @Test
    void confirmBoard_shouldSendBoardConfirmedEvent_withRunningStatus_whenSecondPlayerConfirms() {
        // Arrange: beide Boards ready machen
        gameService.rerollBoard(GAME_CODE, PLAYER_A_ID);
        gameService.rerollBoard(GAME_CODE, PLAYER_B_ID);

        // Act: erst A confirm, dann B confirm (bei B wechselt status -> RUNNING)
        gameService.confirmBoard(GAME_CODE, PLAYER_A_ID);
        Game savedAfterSecond = gameService.confirmBoard(GAME_CODE, PLAYER_B_ID);

        // Assert
        assertEquals(GameStatus.RUNNING, savedAfterSecond.getStatus());

        // Wir prüfen nur das letzte gesendete Event (es wurden 2 gesendet)
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto lastEvt = captor.getAllValues().get(1);
        assertEquals(GameEventType.BOARD_CONFIRMED, lastEvt.type());
        assertEquals(GameStatus.RUNNING, lastEvt.gameStatus());
        assertEquals(PLAYER_B_ID.toString(), lastEvt.playerId());
    }

    @Test
    void confirmBoard_shouldNotSendEvent_whenGameNotFound() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> gameService.confirmBoard(GAME_CODE, PLAYER_A_ID));

        verifyNoInteractions(messagingTemplate);
    }
}
