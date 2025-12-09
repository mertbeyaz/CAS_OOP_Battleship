package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.web.api.controller.ChatWebSocketController;
import ch.battleship.battleshipbackend.web.api.dto.ChatMessageDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerWebSocketTest {

    @Mock
    GameRepository gameRepository;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    ChatWebSocketController chatController;

    @Test
    void handleChatMessage_shouldBroadcastToTopic_whenSenderIsValid() {
        String gameCode = "TEST-CODE";
        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        Player player = new Player("Attacker");
        game.addPlayer(player);

        UUID senderId = UUID.randomUUID();
        setId(player, senderId); // Spieler bekommt eine ID, wie aus der DB

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.of(game));

        ChatWebSocketController.IncomingChatMessage incoming =
                new ChatWebSocketController.IncomingChatMessage(senderId, "Hallo");

        // Act
        chatController.handleChatMessage(incoming, gameCode);

        // Assert
        verify(gameRepository).findByGameCode(gameCode);
        verify(gameRepository).save(game);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/games/" + gameCode + "/chat"),
                any(ChatMessageDto.class)
        );

        verifyNoMoreInteractions(gameRepository, messagingTemplate);
    }

    @Test
    void handleChatMessage_shouldThrow_whenSenderNotInGame() {
        // Arrange
        String gameCode = "TEST-CODE";
        Game game = new Game(gameCode, GameConfiguration.defaultConfig());

        // Game hat KEINE Spieler oder zumindest keinen mit der gesendeten ID
        Player existingPlayer = new Player("Existing");
        game.addPlayer(existingPlayer);

        UUID unknownSenderId = UUID.randomUUID(); // gehört NICHT zu existingPlayer

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.of(game));

        ChatWebSocketController.IncomingChatMessage incoming =
                new ChatWebSocketController.IncomingChatMessage(unknownSenderId, "Hallo");

        // Act
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> chatController.handleChatMessage(incoming, gameCode)
        );

        // Assert
        assertThat(ex.getMessage()).isEqualTo("Sender does not belong to this game");

        // Game wurde zwar geladen...
        verify(gameRepository).findByGameCode(gameCode);
        // ...aber nicht gespeichert
        verifyNoMoreInteractions(gameRepository);

        // Kein WebSocket-Event wurde gesendet
        verifyNoInteractions(messagingTemplate);
    }
}
