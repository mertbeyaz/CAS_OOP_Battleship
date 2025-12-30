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

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Create a new game")
    @PostMapping
    public ResponseEntity<CreateGameResponseDto> createGame() {
        var game = gameService.createNewGame();
        return ResponseEntity.ok(new CreateGameResponseDto(game.getGameCode()));
    }

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

    @Operation(summary = "Fire a shot in a game")
    @PostMapping("/{gameCode}/shots")
    public ResponseEntity<ShotResultDto> fireShot(@PathVariable String gameCode,
                                                  @RequestBody ShotRequest request) {
        try {
            Shot shot = gameService.fireShot(gameCode, request.shooterId(), request.x(), request.y());
            ShotResult r = shot.getResult();

            // Nach fireShot ist der Turn im Game bereits korrekt gesetzt
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
