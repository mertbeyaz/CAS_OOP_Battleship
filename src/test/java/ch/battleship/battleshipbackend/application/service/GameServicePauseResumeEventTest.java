package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.GameEventDto;
import ch.battleship.battleshipbackend.web.api.dto.GameResumeResponseDto;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for pause/resume behavior in {@link GameService}.
 *
 * <p>Focus:
 * <ul>
 *   <li>{@link GameService#pauseGame(String, UUID)}: RUNNING -> PAUSED, reset resume handshake</li>
 *   <li>{@link GameService#resumeGame(String, UUID)}: 2-step handshake
 *       (PAUSED -> WAITING -> RUNNING)</li>
 *   <li>WebSocket events are broadcast to {@code /topic/games/{gameCode}/events}</li>
 *   <li>Invalid states must not emit WebSocket events</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GameServicePauseResumeEventTest {

    private static final String GAME_CODE = "TEST-GAME";
    private static final UUID PLAYER_A_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Mock private GameRepository gameRepository;
    @Mock private ShotRepository shotRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private GameService gameService;

    private Game game;
    private Player playerA;
    private Player playerB;

    @BeforeEach
    void setUp() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        game = new Game(GAME_CODE, config);

        playerA = new Player("PlayerA");
        setId(playerA, PLAYER_A_ID);

        playerB = new Player("PlayerB");
        setId(playerB, PLAYER_B_ID);

        game.addPlayer(playerA);
        game.addPlayer(playerB);
    }

    @Test
    void pauseGame_shouldSetPaused_resetResumeReady_andSendGamePausedEvent() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        game.setStatus(GameStatus.RUNNING);
        game.setResumeReadyPlayerId(UUID.randomUUID()); // simulate stale handshake

        // Act
        Game updated = gameService.pauseGame(GAME_CODE, PLAYER_A_ID);

        // Assert: state transition
        assertEquals(GameStatus.PAUSED, updated.getStatus());
        assertNull(updated.getResumeReadyPlayerId());

        // Assert: WS event
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.GAME_PAUSED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.PAUSED, evt.gameStatus());
        assertEquals("PlayerA", evt.payload().get("requestedByPlayerName"));
    }

    @Test
    void pauseGame_shouldFail_whenNotRunning() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        game.setStatus(GameStatus.SETUP);

        // Act + Assert
        assertThatThrownBy(() -> gameService.pauseGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pause");

        // No broadcast on invalid state (disambiguate overloaded convertAndSend).
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void resumeGame_firstPlayer_shouldSetWaiting_andSendResumePendingEvent() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        game.setStatus(GameStatus.PAUSED);
        game.setResumeReadyPlayerId(null);

        // Act
        GameResumeResponseDto resp = gameService.resumeGame(GAME_CODE, PLAYER_A_ID);

        // Assert: handshake step 1 (PAUSED -> WAITING)
        assertEquals(GameStatus.WAITING, resp.status());
        assertThat(resp.handshakeComplete()).isFalse();
        assertThat(resp.snapshot()).isNotNull();

        // Assert: WS event RESUME_PENDING
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.GAME_RESUME_PENDING, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.WAITING, evt.gameStatus());
        assertEquals("PlayerA", evt.payload().get("requestedByPlayerName"));
    }

    @Test
    void resumeGame_secondPlayer_shouldSetRunning_andSendGameResumedEvent() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Handshake already started by PlayerA
        game.setStatus(GameStatus.WAITING);
        game.setResumeReadyPlayerId(PLAYER_A_ID);

        // Act
        GameResumeResponseDto resp = gameService.resumeGame(GAME_CODE, PLAYER_B_ID);

        // Assert: handshake complete (WAITING -> RUNNING)
        assertEquals(GameStatus.RUNNING, resp.status());
        assertThat(resp.handshakeComplete()).isTrue();
        assertThat(resp.snapshot()).isNotNull();

        // Assert: WS event RESUMED
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.GAME_RESUMED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.RUNNING, evt.gameStatus());
        assertEquals("PlayerB", evt.payload().get("requestedByPlayerName"));
    }

    @Test
    void resumeGame_shouldFail_whenNotPausedOrWaiting() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        game.setStatus(GameStatus.SETUP);

        // Act + Assert
        assertThatThrownBy(() -> gameService.resumeGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resume");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void resumeGame_shouldFail_whenWaitingButNotInResumeHandshake() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));

        // WAITING is also used for normal join/setup;
        // resume is only allowed if resumeReadyPlayerId is already set (handshake started).
        game.setStatus(GameStatus.WAITING);
        game.setResumeReadyPlayerId(null);

        // Act + Assert
        assertThatThrownBy(() -> gameService.resumeGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resume-handshake");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void pauseGame_shouldThrowNotFound_whenGameMissing() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> gameService.pauseGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(messagingTemplate);
    }
}
