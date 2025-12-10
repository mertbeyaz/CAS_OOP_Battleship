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

@RestController
@RequestMapping("/api/games/{gameCode}/chat")
public class ChatController {

    private final GameRepository gameRepository;

    public ChatController(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

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
