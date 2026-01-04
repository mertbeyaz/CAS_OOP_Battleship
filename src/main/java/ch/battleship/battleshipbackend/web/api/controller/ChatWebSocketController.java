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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WebSocket (STOMP) controller handling in-game chat messages.
 *
 * <p>Clients send chat messages to an application destination (configured with prefix {@code /app}),
 * e.g. {@code /app/games/{gameCode}/chat}. The server validates the sender, persists the message
 * in the game history and broadcasts a DTO to subscribers via a topic destination, e.g.
 * {@code /topic/games/{gameCode}/chat}.
 *
 * <p>Security note:
 * The sender id is validated against the game's player list to prevent sending messages as another player.
 */
@Controller
public class ChatWebSocketController {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates a new {@code ChatWebSocketController}.
     *
     * @param gameRepository repository used to load and persist game state including chat messages
     * @param messagingTemplate used to broadcast chat messages to subscribed clients
     */
    public ChatWebSocketController(GameRepository gameRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.gameRepository = gameRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles an incoming chat message for the given game and broadcasts it to all subscribers.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Load game by {@code gameCode}</li>
     *   <li>Validate that {@code senderId} belongs to a player in the game</li>
     *   <li>Persist chat message as part of the game history</li>
     *   <li>Broadcast a {@code ChatMessageDto} to {@code /topic/games/{gameCode}/chat}</li>
     * </ol>
     *
     * <p>The method runs within a transaction to ensure the persisted chat history is consistent
     * with the message broadcast.
     *
     * @param incoming incoming chat payload containing sender id and message text
     * @param gameCode game code extracted from the STOMP destination
     * @throws EntityNotFoundException if the game does not exist
     * @throws IllegalArgumentException if the sender does not belong to the game
     */
    @Transactional
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

    /**
     * Incoming chat message payload sent by clients via WebSocket.
     *
     * @param senderId player id of the sender
     * @param message message text content
     */
    public record IncomingChatMessage(
            UUID senderId,
            String message
    ) {}
}
