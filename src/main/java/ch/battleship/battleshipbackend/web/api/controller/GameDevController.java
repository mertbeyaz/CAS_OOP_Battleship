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

/**
 * Development-only REST controller exposing debugging endpoints.
 *
 * <p>This controller is enabled only for the {@code dev} and {@code test} Spring profiles.
 * The endpoints intentionally reveal hidden game information (e.g. ship placements)
 * and must therefore never be available in production environments.
 *
 * <p>Error handling strategy (simplified):
 * <ul>
 *   <li>{@link EntityNotFoundException} -> 404 Not Found</li>
 *   <li>{@link IllegalStateException} -> 400 Bad Request</li>
 * </ul>
 */
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/dev/games")
public class GameDevController {

    private final GameService gameService;

    /**
     * Creates a new {@code GameDevController}.
     *
     * @param gameService service providing dev-only helper methods
     */
    public GameDevController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * Returns an ASCII representation of a board.
     *
     * <p>This endpoint is intended for debugging and development. If {@code showShips=true},
     * ship placements are rendered, which would allow cheating in a real game.
     *
     * @param gameCode game identifier
     * @param boardId board identifier
     * @param showShips if {@code true}, ship positions are shown (debugging only)
     * @return 200 OK with plain text ASCII output,
     *         404 if the game does not exist, or 400 if the board does not belong to the game
     */
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

    /**
     * Returns the full state of a specific board including ship placements.
     *
     * <p>Development-only endpoint to inspect the board state during implementation and testing.
     * This must not be exposed in production because it reveals hidden information.
     *
     * @param gameCode game identifier
     * @param boardId board identifier
     * @return 200 OK with {@link BoardStateDto},
     *         404 if the game does not exist, or 400 if the board does not belong to the game
     */
    @Operation(summary = "Get a specific board state")
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
