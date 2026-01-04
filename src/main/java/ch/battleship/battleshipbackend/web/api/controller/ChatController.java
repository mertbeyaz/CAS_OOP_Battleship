package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.domain.ChatMessage;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.web.api.dto.ChatMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * REST controller exposing read-only chat history endpoints for a game.
 *
 * <p>This controller provides the persisted chat messages of a game as DTOs.
 * Sorting is applied by creation time to return the conversation in chronological order.
 *
 * <p>Note:
 * Chat message sender is mapped from a {@code ManyToOne(fetch = LAZY)} association.
 * Therefore the endpoint runs within a read-only transaction to allow lazy loading
 * while still keeping the implementation simple.
 */
@RestController
@RequestMapping("/api/games/{gameCode}/chat")
public class ChatController {

    private final GameRepository gameRepository;

    /**
     * Creates a new {@code ChatController}.
     *
     * @param gameRepository repository used to load games including their chat messages
     */
    public ChatController(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    /**
     * Returns the chat history of a game ordered by creation time ascending.
     *
     * @param gameCode public game identifier
     * @return list of chat messages as DTOs, sorted chronologically
     * @throws EntityNotFoundException if no game exists for the given gameCode
     */
    @Operation(summary = "List the chat history of a specific game")
    @GetMapping("/messages")
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatHistory(@PathVariable String gameCode) {
        Game game = gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameCode));

        return game.getMessages().stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(msg -> new ChatMessageDto(
                        msg.getSender().getId(),
                        msg.getSender().getUsername(),
                        gameCode,
                        msg.getText(),
                        msg.getCreatedAt()
                ))
                .toList();
    }
}
