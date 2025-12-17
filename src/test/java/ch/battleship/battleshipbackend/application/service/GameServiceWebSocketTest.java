package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.*;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.ShotRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.GameEventDto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Spezielle Tests für das WebSocket-Verhalten von GameService.fireShot().
 * Die fachliche Logik wird bereits in GameServiceTest/GameShootingTest geprüft,
 * hier fokussieren wir nur auf den WS-Event (ShotEventDto + Destination).
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

// Klasse: GameServiceWebSocketTest
// Methode: fireShot_shouldSendWebSocketEvent_whenShotIsValid

    @Test
    void fireShot_shouldSendWebSocketEvent_whenShotIsValid() {
        // Arrange
        GameConfiguration config = GameConfiguration.defaultConfig();
        String code = "TEST-CODE";
        Game game = new Game(code, config);
        game.setStatus(GameStatus.RUNNING);

        Player attacker = new Player("Attacker");
        Player defender = new Player("Defender");
        game.addPlayer(attacker);
        game.addPlayer(defender);

        Board defenderBoard = new Board(
                game.getConfig().getBoardWidth(),
                game.getConfig().getBoardHeight(),
                defender
        );

        Ship ship = new Ship(ShipType.DESTROYER); // size 2
        defenderBoard.placeShip(
                ship,
                new Coordinate(3, 3),
                Orientation.HORIZONTAL
        );

        game.addBoard(defenderBoard);

        // IDs simulieren (persistierter Zustand)
        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        setId(attacker, attackerId);
        setId(defender, defenderId);
        setId(defenderBoard, boardId);

        // IMPORTANT: Turn setzen, sonst "Game has no current turn player set"
        game.setCurrentTurnPlayerId(attackerId);

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shotRepository.save(any(Shot.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<GameEventDto> eventCaptor = ArgumentCaptor.forClass(GameEventDto.class);

        // Act
        gameService.fireShot(code, attackerId, boardId, 3, 3);

        // Assert – jetzt /events und GameEventDto
        String expectedDestination = "/topic/games/" + code + "/events";

        // fireShot sendet bei euch mindestens SHOT_FIRED und meistens auch TURN_CHANGED
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq(expectedDestination), eventCaptor.capture());

        // wir suchen explizit das SHOT_FIRED Event (weil evtl. TURN_CHANGED auch kommt)
        GameEventDto shotEvt = eventCaptor.getAllValues().stream()
                .filter(e -> e.type() == GameEventType.SHOT_FIRED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected SHOT_FIRED event"));

        assertThat(shotEvt).isNotNull();
        assertThat(shotEvt.gameCode()).isEqualTo(code);
        assertThat(shotEvt.gameStatus()).isEqualTo(GameStatus.RUNNING);

        // Payload Assertions (entspricht GameEventDto.shotFired(...))
        assertThat(shotEvt.payload().get("attackerName")).isEqualTo("Attacker");
        assertThat(shotEvt.payload().get("defenderName")).isEqualTo("Defender");
        assertThat(shotEvt.payload().get("x")).isEqualTo(3);
        assertThat(shotEvt.payload().get("y")).isEqualTo(3);
        assertThat(shotEvt.payload().get("result")).isIn("HIT", "SUNK");
        assertThat(shotEvt.payload().get("hit")).isEqualTo(true);

        // Destroyer size 2: nach 1 Treffer nicht versenkt
        assertThat(shotEvt.payload().get("shipSunk")).isEqualTo(false);

        // currentTurnPlayerId ist im Payload enthalten (bei HIT bleibt attacker dran)
        assertThat(shotEvt.payload().get("currentTurnPlayerId")).isEqualTo(attackerId.toString());

        // Repository wurde wie erwartet benutzt
        verify(gameRepository, times(1)).findByGameCode(code);
        verify(gameRepository, atLeastOnce()).save(any(Game.class));
        verify(shotRepository, times(1)).save(any(Shot.class));
    }


    @Test
    void fireShot_shouldNotSendEvent_whenGameNotFound() {
        // Arrange
        String code = "UNKNOWN";
        when(gameRepository.findByGameCode(code)).thenReturn(Optional.empty());

        // Act / Assert – Exception wird in GameServiceTest geprüft,
        // hier interessiert uns nur, dass KEIN Event gesendet wird.
        try {
            gameService.fireShot(code, UUID.randomUUID(), UUID.randomUUID(), 0, 0);
        } catch (Exception ignored) {
            // fachliche Exceptions sind hier egal
        }

        verifyNoInteractions(messagingTemplate);
    }

    // Helper, analog zu GameServiceTest, um IDs per Reflection zu setzen
    private void setId(BaseEntity entity, UUID id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id via reflection", e);
        }
    }
}
