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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServicePauseResumeEventTest {

    private static final String GAME_CODE = "TEST-GAME";
    private static final UUID PLAYER_A_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Mock private GameRepository gameRepository;
    @Mock private ShotRepository shotRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private GameService gameService;

    private Game game;
    private Player playerA;

    @BeforeEach
    void setUp() {
        GameConfiguration config = GameConfiguration.defaultConfig();
        game = new Game(GAME_CODE, config);

        playerA = new Player("PlayerA");
        setId(playerA, PLAYER_A_ID);

        game.addPlayer(playerA);


    }

    @Test
    void pauseGame_shouldSetPaused_andSendGamePausedEvent() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Arrange
        game.setStatus(GameStatus.RUNNING);

        // Act
        Game updated = gameService.pauseGame(GAME_CODE, PLAYER_A_ID);

        // Assert
        assertEquals(GameStatus.PAUSED, updated.getStatus());

        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.GAME_PAUSED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.PAUSED, evt.gameStatus());
        //assertEquals(PLAYER_A_ID.toString(), evt.payload().get("requestedByPlayerId"));
        assertEquals("PlayerA", evt.payload().get("requestedByPlayerName"));
    }

    @Test
    void pauseGame_shouldFail_whenNotRunning() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        // Arrange
        game.setStatus(GameStatus.SETUP);

        // Act + Assert
        assertThatThrownBy(() -> gameService.pauseGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pause");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

    }

    @Test
    void resumeGame_shouldSetRunning_andSendGameResumedEvent() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Arrange
        game.setStatus(GameStatus.PAUSED);

        // Act
        Game updated = gameService.resumeGame(GAME_CODE, PLAYER_A_ID);

        // Assert
        assertEquals(GameStatus.RUNNING, updated.getStatus());

        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        GameEventDto evt = captor.getValue();
        assertEquals(GameEventType.GAME_RESUMED, evt.type());
        assertEquals(GAME_CODE, evt.gameCode());
        assertEquals(GameStatus.RUNNING, evt.gameStatus());
        assertEquals("PlayerA", evt.payload().get("requestedByPlayerName"));
    }

    @Test
    void resumeGame_shouldFail_whenNotPaused() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));

        // Arrange
        game.setStatus(GameStatus.RUNNING);

        // Act + Assert
        assertThatThrownBy(() -> gameService.resumeGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resume");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

    }

    @Test
    void pauseGame_shouldReturnNotFound_whenGameMissing() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        //when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.pauseGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(messagingTemplate);
    }
}

