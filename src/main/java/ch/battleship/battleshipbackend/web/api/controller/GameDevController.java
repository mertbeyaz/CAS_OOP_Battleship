package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.BoardStateDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/dev/games")
public class GameDevController {

    private final GameService gameService;

    public GameDevController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Get ASCII view of a board")
    @GetMapping(
            value = "/{gameCode}/boards/{boardId}/ascii",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> getBoardAscii(@PathVariable String gameCode,
                                                @PathVariable UUID boardId,
                                                @RequestParam(name = "showShips", defaultValue = "true")
                                                boolean showShips) {
        try {
            String ascii = gameService.getBoardAscii(gameCode, boardId, showShips);
            return ResponseEntity.ok(ascii);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get a specific board state ")
    @GetMapping("/{gameCode}/boards/{boardId}/state")
    public ResponseEntity<BoardStateDto> getBoardState(@PathVariable String gameCode,
                                                       @PathVariable UUID boardId) {
        try {
            BoardStateDto state = gameService.getBoardState(gameCode, boardId);
            return ResponseEntity.ok(state);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
