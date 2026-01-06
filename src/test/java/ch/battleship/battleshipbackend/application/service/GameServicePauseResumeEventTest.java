package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.GameResumeToken;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.GameResumeTokenRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for pause/resume behavior in {@link GameService}.
 *
 * <p>Focus:
 * <ul>
 *   <li>{@link GameService#pauseGame(String, UUID)}: RUNNING -> PAUSED, reset resume handshake</li>
 *   <li>{@link GameService#resumeGame(String)}: token-based 2-step handshake
 *       (PAUSED -> WAITING -> RUNNING)</li>
 *   <li>WebSocket events are broadcast to {@code /topic/games/{gameCode}/events}</li>
 *   <li>Invalid states must not emit WebSocket events</li>
 * </ul>
 *
 * <p>Note:
 * The legacy resume method (gameCode + playerId) was replaced by token-based resume.
 * Therefore, these tests resolve the player context via {@link GameResumeTokenRepository}.</p>
 */
@ExtendWith(MockitoExtension.class)
class GameServicePauseResumeEventTest {

    private static final String GAME_CODE = "TEST-GAME";

    private static final UUID PLAYER_A_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private static final String TOKEN_A = "token-player-a";
    private static final String TOKEN_B = "token-player-b";

    @Mock private GameRepository gameRepository;
    @Mock private ShotRepository shotRepository; // injected by GameService ctor
    @Mock private GameResumeTokenRepository gameResumeTokenRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private GameService gameService;

    private Game game;
    private Player playerA;
    private Player playerB;

    private GameResumeToken tokenA;
    private GameResumeToken tokenB;

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

        tokenA = new GameResumeToken(TOKEN_A, game, playerA);
        tokenB = new GameResumeToken(TOKEN_B, game, playerB);
    }

    /**
     * Ensures that {@link GameService#pauseGame(String, UUID)} transitions RUNNING -> PAUSED,
     * clears the handshake marker and emits {@code GAME_PAUSED}.
     */
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

    /**
     * Ensures that pausing a non-RUNNING game fails and does not emit any WebSocket event.
     */
    @Test
    void pauseGame_shouldFail_whenNotRunning() {
        // Arrange
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        game.setStatus(GameStatus.SETUP);

        // Act + Assert
        assertThatThrownBy(() -> gameService.pauseGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pause");

        // No broadcast on invalid state
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    /**
     * Ensures that the first resume confirmation (token-based) transitions PAUSED -> WAITING,
     * stores {@code resumeReadyPlayerId} and emits {@code GAME_RESUME_PENDING}.
     *
     * <p>Important detail:
     * On step 1 the game is not RUNNING yet, therefore {@code currentTurnPlayerName} must be null.</p>
     */
    @Test
    void resumeGame_firstPlayer_shouldSetWaiting_andSendResumePendingEvent() {
        // Arrange
        when(gameResumeTokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(tokenA));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameResumeTokenRepository.save(any(GameResumeToken.class))).thenAnswer(inv -> inv.getArgument(0));

        game.setStatus(GameStatus.PAUSED);
        game.setResumeReadyPlayerId(null);

        // Act
        GameResumeResponseDto resp = gameService.resumeGame(TOKEN_A);

        // Assert: handshake step 1 (PAUSED -> WAITING)
        assertEquals(GAME_CODE, resp.gameCode());
        assertEquals(GameStatus.WAITING, resp.status());
        assertThat(resp.handshakeComplete()).isFalse();
        assertThat(resp.requestedByPlayerName()).isEqualTo("PlayerA");

        // Not running yet -> no reliable turn announcement
        assertNull(resp.currentTurnPlayerName());

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

    /**
     * Ensures that the second resume confirmation (token-based) transitions WAITING -> RUNNING,
     * clears {@code resumeReadyPlayerId} and emits {@code GAME_RESUMED}.
     *
     * <p>Now the handshake is complete and the turn is reliable, therefore the response must
     * include {@code currentTurnPlayerName}.</p>
     */
    @Test
    void resumeGame_secondPlayer_shouldSetRunning_andSendGameResumedEvent() {
        // Arrange
        when(gameResumeTokenRepository.findByToken(TOKEN_B)).thenReturn(Optional.of(tokenB));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameResumeTokenRepository.save(any(GameResumeToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Handshake already started by PlayerA
        game.setStatus(GameStatus.WAITING);
        game.setResumeReadyPlayerId(PLAYER_A_ID);

        // Ensure a deterministic current-turn for this test
        game.setCurrentTurnPlayerId(PLAYER_A_ID);

        // Act
        GameResumeResponseDto resp = gameService.resumeGame(TOKEN_B);

        // Assert: handshake complete (WAITING -> RUNNING)
        assertEquals(GAME_CODE, resp.gameCode());
        assertEquals(GameStatus.RUNNING, resp.status());
        assertThat(resp.handshakeComplete()).isTrue();
        assertThat(resp.requestedByPlayerName()).isEqualTo("PlayerB");

        // Now we are RUNNING -> turn name must be available
        assertThat(resp.currentTurnPlayerName()).isEqualTo("PlayerA");

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

    /**
     * Ensures that resume fails for invalid game states (neither PAUSED nor resume-handshake WAITING)
     * and does not emit any WebSocket event.
     */
    @Test
    void resumeGame_shouldFail_whenNotPausedOrWaiting() {
        // Arrange
        when(gameResumeTokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(tokenA));
        when(gameResumeTokenRepository.save(any(GameResumeToken.class))).thenAnswer(inv -> inv.getArgument(0));

        game.setStatus(GameStatus.SETUP);

        // Act + Assert
        assertThatThrownBy(() -> gameService.resumeGame(TOKEN_A))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resume");

        verifyNoInteractions(messagingTemplate);
    }

    /**
     * Ensures that WAITING games cannot be resumed unless the handshake is already started
     * (resumeReadyPlayerId must be set).
     */
    @Test
    void resumeGame_shouldFail_whenWaitingButNotInResumeHandshake() {
        // Arrange
        when(gameResumeTokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(tokenA));
        when(gameResumeTokenRepository.save(any(GameResumeToken.class))).thenAnswer(inv -> inv.getArgument(0));

        game.setStatus(GameStatus.WAITING);
        game.setResumeReadyPlayerId(null);

        // Act + Assert
        assertThatThrownBy(() -> gameService.resumeGame(TOKEN_A))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resume-handshake");

        verifyNoInteractions(messagingTemplate);
    }

    /**
     * Ensures that resuming with an unknown token throws {@link EntityNotFoundException}
     * and does not emit any WebSocket event.
     */
    @Test
    void resumeGame_shouldThrowNotFound_whenTokenMissing() {
        // Arrange
        when(gameResumeTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> gameService.resumeGame("missing-token"))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(messagingTemplate);
    }

    /**
     * Ensures that pausing a missing game throws {@link EntityNotFoundException} and does not emit events.
     */
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
