package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.GameEventDto;
import jakarta.persistence.EntityNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WebSocket-focused unit tests for {@link GameService#fireShot(String, UUID, int, int)}.
 *
 * <p>The functional shot logic (validation, hit/miss rules, persistence) is covered in {@code GameServiceTest}.
 * This class focuses on the <b>WebSocket side effects</b>:
 * <ul>
 *   <li>Which destination is used (topic)</li>
 *   <li>Which event type is emitted ({@link GameEventType#SHOT_FIRED})</li>
 *   <li>That the payload contains only public data (no IDs leaking)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GameServiceWebSocketTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ShotRepository shotRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    /**
     * Ensures that a valid shot triggers a WS event on the game events topic.
     *
     * <p>We expect at least one call to:
     * {@code /topic/games/{gameCode}/events}
     *
     * <p>Depending on the current logic, {@code fireShot} may send multiple events
     * (e.g. {@code SHOT_FIRED} and {@code TURN_CHANGED}). Therefore we capture all and
     * explicitly assert the {@code SHOT_FIRED} event.
     */
    @Test
    void fireShot_shouldSendWebSocketEvent_whenShotIsValid() {
        // Arrange: Running game with 2 players
        GameConfiguration config = GameConfiguration.defaultConfig();
        String gameCode = "TEST-CODE";
        Game game = new Game(gameCode, config);
        game.setStatus(GameStatus.RUNNING);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        // Defender board with a destroyer at (3,3) horizontal -> guarantees HIT at (3,3)
        Board defenderBoard = new Board(config.getBoardWidth(), config.getBoardHeight(), defender);
        defenderBoard.placeShip(new Ship(ShipType.DESTROYER), new Coordinate(3, 3), Orientation.HORIZONTAL);
        game.addBoard(defenderBoard);

        // Simulate persisted IDs
        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        UUID defenderBoardId = UUID.randomUUID();
        setId(attacker, attackerId);
        setId(defender, defenderId);
        setId(defenderBoard, defenderBoardId);

        // IMPORTANT: current turn must be set, otherwise service may reject firing
        game.setCurrentTurnPlayerId(attackerId);

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shotRepository.save(any(Shot.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<GameEventDto> eventCaptor = ArgumentCaptor.forClass(GameEventDto.class);

        // Act
        gameService.fireShot(gameCode, attackerId, 3, 3);

        // Assert: destination and emitted events
        String expectedDestination = "/topic/games/" + gameCode + "/events";

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq(expectedDestination), eventCaptor.capture());

        GameEventDto shotEvt = eventCaptor.getAllValues().stream()
                .filter(e -> e.type() == GameEventType.SHOT_FIRED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected SHOT_FIRED event"));

        assertThat(shotEvt.gameCode()).isEqualTo(gameCode);
        assertThat(shotEvt.gameStatus()).isEqualTo(GameStatus.RUNNING);

        // Payload must contain shot info but not leak internal IDs
        assertThat(shotEvt.payload()).containsKeys("x", "y", "result", "hit", "shipSunk");
        assertThat(shotEvt.payload()).doesNotContainKeys(
                "attackerId", "defenderId", "currentTurnPlayerId"
        );

        assertThat(shotEvt.payload().get("x")).isEqualTo(3);
        assertThat(shotEvt.payload().get("y")).isEqualTo(3);

        // result is a string (via GameEventDto factory): "HIT" or "SUNK"
        assertThat(shotEvt.payload().get("result")).isIn("HIT", "SUNK");
        assertThat(shotEvt.payload().get("hit")).isEqualTo(true);

        // For a destroyer (size 2), first hit should not sink it.
        // If your domain logic can sink on first hit (shouldn't), this would fail intentionally.
        assertThat(shotEvt.payload().get("shipSunk")).isEqualTo(false);

        // Repository interactions
        verify(gameRepository, times(1)).findByGameCode(gameCode);
        verify(gameRepository, atLeastOnce()).save(any(Game.class));
        verify(shotRepository, times(1)).save(any(Shot.class));
        verifyNoMoreInteractions(shotRepository);
    }

    /**
     * If the game does not exist, no WS events should be emitted.
     *
     * <p>The exact exception type is owned by the service contract; we assert
     * the common case {@link EntityNotFoundException} and additionally verify
     * that the messaging template is never touched.
     */
    @Test
    void fireShot_shouldNotSendEvent_whenGameNotFound() {
        // Arrange
        String gameCode = "UNKNOWN";
        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> gameService.fireShot(gameCode, UUID.randomUUID(), 0, 0))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(messagingTemplate);
        verify(gameRepository).findByGameCode(gameCode);
        verifyNoMoreInteractions(gameRepository);
        verifyNoInteractions(shotRepository);
    }
}
