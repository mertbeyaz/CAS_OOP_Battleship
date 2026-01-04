package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.domain.Shot;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing the main game endpoints for the Battleship application.
 *
 * <p>This controller delegates all business logic to {@link GameService} and converts typical
 * domain/service exceptions into HTTP responses.
 *
 * <p>Error handling strategy (simplified):
 * <ul>
 *   <li>{@link EntityNotFoundException} -> 404 Not Found</li>
 *   <li>{@link IllegalStateException} / {@link IllegalArgumentException} -> 400 Bad Request</li>
 * </ul>
 *
 * <p>Note:
 * Authentication is not part of this project; therefore a {@code playerId} is sent by the client
 * and validated server-side against the game state.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    /**
     * Creates a new {@code GameController}.
     *
     * @param gameService service implementing the game use-cases
     */
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * Creates a new game and returns its public game code.
     *
     * @return 200 OK with {@link CreateGameResponseDto} containing the generated gameCode
     */
    @Operation(summary = "Create a new game")
    @PostMapping
    public ResponseEntity<CreateGameResponseDto> createGame() {
        var game = gameService.createNewGame();
        return ResponseEntity.ok(new CreateGameResponseDto(game.getGameCode()));
    }

    /**
     * Joins an existing game as a new player.
     *
     * @param gameCode game identifier
     * @param request join request containing the username
     * @return 200 OK with {@link JoinGameResponseDto} including the generated playerId,
     *         404 if the game does not exist, or 400 if the join is not allowed
     */
    @Operation(summary = "Join a game as a player")
    @PostMapping("/{gameCode}/join")
    public ResponseEntity<JoinGameResponseDto> joinGame(@PathVariable String gameCode,
                                                        @RequestBody JoinGameRequest request) {
        try {
            JoinGameResponseDto dto = gameService.joinGamePublic(gameCode, request.username());
            return ResponseEntity.ok(dto);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Returns a sanitized, client-safe view of the game state for the given player.
     *
     * @param gameCode game identifier
     * @param playerId requesting player id
     * @return 200 OK with {@link GamePublicDto},
     *         404 if the game does not exist, or 400 if playerId is invalid for the game
     */
    @Operation(summary = "Get public game state for a player")
    @GetMapping("/{gameCode}")
    public ResponseEntity<GamePublicDto> getGame(@PathVariable String gameCode,
                                                 @RequestParam UUID playerId) {
        try {
            return ResponseEntity.ok(gameService.getPublicState(gameCode, playerId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Returns a detailed snapshot of the game state for the given player.
     *
     * <p>Used for browser refresh / reconnect scenarios. The snapshot includes own board placements
     * while keeping opponent ship placements hidden.
     *
     * @param gameCode game identifier
     * @param playerId requesting player id
     * @return 200 OK with {@link GameSnapshotDto},
     *         404 if the game does not exist, or 400 if playerId is invalid for the game
     */
    @Operation(summary = "Get full game state snapshot for a player (used for browser refresh / reconnect)")
    @GetMapping("/{gameCode}/state")
    public ResponseEntity<GameSnapshotDto> getGameState(@PathVariable String gameCode,
                                                        @RequestParam UUID playerId) {
        try {
            return ResponseEntity.ok(gameService.getSnapshot(gameCode, playerId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Pauses a running game.
     *
     * @param gameCode game identifier
     * @param request request containing the playerId who requests the pause
     * @return 200 OK with updated {@link GamePublicDto},
     *         404 if the game does not exist, or 400 if the game is not pausable / player invalid
     */
    @Operation(summary = "Pause a running game")
    @PostMapping("/{gameCode}/pause")
    public ResponseEntity<GamePublicDto> pauseGame(@PathVariable String gameCode,
                                                   @RequestBody PlayerActionRequest request) {
        try {
            gameService.pauseGame(gameCode, request.playerId());
            return ResponseEntity.ok(gameService.getPublicState(gameCode, request.playerId()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Initiates or completes the resume handshake for a paused game.
     *
     * <p>The resume operation is implemented as a two-player handshake in the service layer:
     * first player confirms -> WAITING, second player confirms -> RUNNING.
     *
     * @param gameCode game identifier
     * @param request request containing the playerId who requests resume
     * @return 200 OK with {@link GameResumeResponseDto},
     *         404 if the game does not exist, or 400 if resume is not allowed
     */
    @Operation(summary = "Resume a paused game")
    @PostMapping("/{gameCode}/resume")
    public ResponseEntity<GameResumeResponseDto> resumeGame(@PathVariable String gameCode,
                                                            @RequestBody PlayerActionRequest request) {
        try {
            GameResumeResponseDto response = gameService.resumeGame(gameCode, request.playerId());
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Forfeits a running or paused game (concede) and determines the winner.
     *
     * @param gameCode game identifier
     * @param request request containing the forfeiting playerId
     * @return 200 OK with updated {@link GamePublicDto},
     *         404 if the game does not exist, or 400 if forfeit is not allowed
     */
    @Operation(summary = "Forfeit a game (concede)")
    @PostMapping("/{gameCode}/forfeit")
    public ResponseEntity<GamePublicDto> forfeitGame(@PathVariable String gameCode,
                                                     @RequestBody PlayerActionRequest request) {
        try {
            gameService.forfeitGame(gameCode, request.playerId());
            return ResponseEntity.ok(gameService.getPublicState(gameCode, request.playerId()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Fires a shot in a running game for the current turn player.
     *
     * <p>The service applies turn logic and win checks. The response DTO summarizes the outcome
     * for the client (hit, sunk, and whether the player keeps the turn).
     *
     * @param gameCode game identifier
     * @param request shot request containing shooterId and coordinates
     * @return 200 OK with {@link ShotResultDto},
     *         404 if the game does not exist, or 400 if the shot is invalid (wrong turn/state/bounds)
     */
    @Operation(summary = "Fire a shot in a game")
    @PostMapping("/{gameCode}/shots")
    public ResponseEntity<ShotResultDto> fireShot(@PathVariable String gameCode,
                                                  @RequestBody ShotRequest request) {
        try {
            Shot shot = gameService.fireShot(gameCode, request.shooterId(), request.x(), request.y());
            ShotResult r = shot.getResult();

            // After fireShot(...) the turn is already updated in the game state.
            GamePublicDto state = gameService.getPublicState(gameCode, request.shooterId());

            return ResponseEntity.ok(new ShotResultDto(
                    r,
                    r == ShotResult.HIT || r == ShotResult.SUNK,
                    r == ShotResult.SUNK,
                    state.yourTurn()
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Re-rolls (auto-places) the fleet for a player's board during SETUP phase.
     *
     * @param gameCode game identifier
     * @param playerId player owning the board
     * @return 200 OK with {@link BoardStateDto},
     *         404 if the game does not exist, or 400 if the operation is not allowed
     */
    @Operation(summary = "Re-roll (auto-place) the fleet for the player's board (SETUP only)")
    @PostMapping("/{gameCode}/players/{playerId}/board/reroll")
    public ResponseEntity<BoardStateDto> rerollBoard(@PathVariable String gameCode,
                                                     @PathVariable UUID playerId) {
        try {
            BoardStateDto state = gameService.rerollBoard(gameCode, playerId);
            return ResponseEntity.ok(state);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Confirms (locks) a player's board during SETUP phase.
     *
     * <p>When both players confirmed their boards, the game transitions to RUNNING.
     *
     * @param gameCode game identifier
     * @param playerId player owning the board to confirm
     * @return 200 OK with {@link GamePublicDto},
     *         404 if the game does not exist, or 400 if the operation is not allowed
     */
    @Operation(summary = "Confirm (lock) the player's board. When both confirmed -> game becomes RUNNING")
    @PostMapping("/{gameCode}/players/{playerId}/board/confirm")
    public ResponseEntity<GamePublicDto> confirmBoard(@PathVariable String gameCode,
                                                      @PathVariable UUID playerId) {
        try {
            GamePublicDto dto = gameService.confirmBoard(gameCode, playerId);
            return ResponseEntity.ok(dto);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
