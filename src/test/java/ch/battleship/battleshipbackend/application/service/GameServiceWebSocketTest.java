package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.Orientation;
import ch.battleship.battleshipbackend.domain.enums.ShipType;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.ShotEventDto;
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
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    @Test
    void fireShot_shouldSendWebSocketEvent_whenShotIsValid() {
        // Arrange – Setup analog zu GameServiceTest.fireShot_shouldReturnShotAndPersistGame_whenAllDataIsValid

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

        Ship ship = new Ship(ShipType.DESTROYER); // Größe 2
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

        when(gameRepository.findByGameCode(code)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<ShotEventDto> eventCaptor = ArgumentCaptor.forClass(ShotEventDto.class);

        // Act
        gameService.fireShot(
                code,
                attackerId,
                boardId,
                3,
                3
        );

        // Assert – prüfen, dass ein WS-Event gesendet wurde
        String expectedDestination = "/topic/games/" + code;
        verify(messagingTemplate, times(1))
                .convertAndSend(eq(expectedDestination), eventCaptor.capture());

        ShotEventDto event = eventCaptor.getValue();
        assertThat(event).isNotNull();
        assertThat(event.gameCode()).isEqualTo(code);
        assertThat(event.attackerName()).isEqualTo("Attacker");
        assertThat(event.defenderName()).isEqualTo("Defender");
        // Koordinate entsprechend deiner Implementierung im GameService (x + "," + y)
        assertThat(event.coordinate()).isEqualTo("3,3");
        assertThat(event.hit()).isTrue();
        // Destroyer mit einem Treffer ist noch nicht versenkt
        assertThat(event.shipSunk()).isFalse();
        assertThat(event.gameStatus()).isEqualTo(GameStatus.RUNNING);
        // Nächster Spieler = der andere im 2-Spieler-Game
        assertThat(event.nextPlayerName()).isEqualTo("Defender");

        // Repository wurde wie erwartet benutzt
        verify(gameRepository, times(1)).findByGameCode(code);
        verify(gameRepository, times(1)).save(game);
        verifyNoMoreInteractions(gameRepository);
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
