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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceForfeitEventTest {

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
        playerB = new Player("PlayerB");
        setId(playerA, PLAYER_A_ID);
        setId(playerB, PLAYER_B_ID);

        game.addPlayer(playerA);
        game.addPlayer(playerB);

        //when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        //when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void forfeitGame_shouldFinishGame_setWinner_andSendGameForfeitedEvent() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        // Arrange
        game.setStatus(GameStatus.RUNNING);

        // Act
        Game updated = gameService.forfeitGame(GAME_CODE, PLAYER_A_ID);

        // Assert: Game state
        assertEquals(GameStatus.FINISHED, updated.getStatus());
        assertEquals(PLAYER_B_ID, updated.getWinnerPlayerId());

        // Assert: Events
        ArgumentCaptor<GameEventDto> captor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/games/" + GAME_CODE + "/events"), captor.capture());

        // Find GAME_FORFEITED event in captured events
        GameEventDto forfeitedEvt = captor.getAllValues().stream()
                .filter(e -> e.type() == GameEventType.GAME_FORFEITED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected GAME_FORFEITED event"));

        assertEquals(GAME_CODE, forfeitedEvt.gameCode());
        assertEquals(GameStatus.FINISHED, forfeitedEvt.gameStatus());

        assertEquals("PlayerA", forfeitedEvt.payload().get("forfeitingPlayerName"));

        assertEquals("PlayerB", forfeitedEvt.payload().get("winnerPlayerName"));
    }

    @Test
    void forfeitGame_shouldAllowForfeit_whenPaused() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        // Arrange
        game.setStatus(GameStatus.PAUSED);

        // Act
        Game updated = gameService.forfeitGame(GAME_CODE, PLAYER_B_ID);

        // Assert
        assertEquals(GameStatus.FINISHED, updated.getStatus());
        assertEquals(PLAYER_A_ID, updated.getWinnerPlayerId());
    }

    @Test
    void forfeitGame_shouldFail_whenGameNotRunningOrPaused() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        // Arrange
        game.setStatus(GameStatus.SETUP);

        // Act + Assert
        assertThatThrownBy(() -> gameService.forfeitGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forfeit");

        // disambiguate overloaded convertAndSend(...)
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void forfeitGame_shouldFail_whenPlayerNotPartOfGame() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.of(game));
        //when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        // Arrange
        game.setStatus(GameStatus.RUNNING);
        UUID outsider = UUID.fromString("20000000-0000-0000-0000-0000000000FF");

        // Act + Assert
        assertThatThrownBy(() -> gameService.forfeitGame(GAME_CODE, outsider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("belong");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void forfeitGame_shouldThrowNotFound_whenGameMissing() {
        when(gameRepository.findByGameCode(GAME_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.forfeitGame(GAME_CODE, PLAYER_A_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(messagingTemplate);
    }
}
