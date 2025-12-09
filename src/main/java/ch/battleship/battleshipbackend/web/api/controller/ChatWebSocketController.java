package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.domain.ChatMessage;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.web.api.dto.ChatMessageDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;  // ⬅️ wichtig

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Controller
public class ChatWebSocketController {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(GameRepository gameRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional   // ⬅️ DAS ist der Fix
    @MessageMapping("/games/{gameCode}/chat")
    public void handleChatMessage(@Payload IncomingChatMessage incoming,
                                  @DestinationVariable String gameCode) {

        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        UUID senderId = incoming.senderId();

        Player sender = game.getPlayers().stream()
                .filter(p -> Objects.equals(p.getId(), senderId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sender does not belong to this game"));

        ChatMessage chatMessage = new ChatMessage(incoming.message(), sender);
        game.addMessage(chatMessage);

        gameRepository.save(game);

        ChatMessageDto dto = new ChatMessageDto(
                sender.getId(),
                sender.getUsername(),
                gameCode,
                incoming.message(),
                Instant.now()
        );

        String destination = "/topic/games/" + gameCode + "/chat";
        messagingTemplate.convertAndSend(destination, dto);
    }

    public record IncomingChatMessage(
            UUID senderId,
            String message
    ) {}
}
