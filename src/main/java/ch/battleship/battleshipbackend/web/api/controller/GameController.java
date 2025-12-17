package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.service.GameService;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.web.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.MediaType;
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

    // Neues Game anlegen
    @Operation(summary = "Create a new game")
    @PostMapping
    public ResponseEntity<GameDto> createGame() {
        Game game = gameService.createNewGame();
        return ResponseEntity.ok(GameDto.from(game));
    }

    // Game per gameCode laden
    @Operation(summary = "Get a game by its code")
    @GetMapping("/{gameCode}")
    public ResponseEntity<GameDto> getGame(@PathVariable String gameCode) {
        return gameService.getByGameCode(gameCode)
                .map(game -> ResponseEntity.ok(GameDto.from(game)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Join a game as a player")
    @PostMapping("/{gameCode}/join")
    public ResponseEntity<GameDto> joinGame(@PathVariable String gameCode,
                                            @RequestBody JoinGameRequest request) {
        try {
            Game game = gameService.joinGame(gameCode, request.username());
            return ResponseEntity.ok(GameDto.from(game));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build(); // später evtl. aussagekräftigere Fehler
        }
    }

    @Operation(summary = "Fire a shot to a specific board")
    @PostMapping("/{gameCode}/boards/{boardId}/shots")
    public ResponseEntity<GameDto> fireShot(@PathVariable String gameCode,
                                            @PathVariable UUID boardId,
                                            @RequestBody ShotRequest request) {
        try {
            gameService.fireShot(
                    gameCode,
                    request.shooterId(),
                    boardId,
                    request.x(),
                    request.y()
            );

            // Nach dem Schuss den aktuellen Game-State zurückgeben
            return gameService.getByGameCode(gameCode)
                    .map(g -> ResponseEntity.ok(GameDto.from(g)))
                    .orElseGet(() -> ResponseEntity.notFound().build());

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
    public ResponseEntity<GameDto> confirmBoard(@PathVariable String gameCode, @PathVariable UUID playerId) {
        try {
            Game game = gameService.confirmBoard(gameCode, playerId);
            return ResponseEntity.ok(GameDto.from(game));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}