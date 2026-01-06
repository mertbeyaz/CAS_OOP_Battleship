package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.repository.GameResumeTokenRepository;
import ch.battleship.battleshipbackend.service.LobbyService;
import ch.battleship.battleshipbackend.web.api.dto.JoinLobbyRequest;
import ch.battleship.battleshipbackend.web.api.dto.LobbyDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller providing lobby matchmaking endpoints.
 *
 * <p>The lobby layer implements a simple matchmaking strategy:
 * clients call {@code /auto-join} to either join an existing open lobby or create a new one.
 *
 * <p>DTO note:
 * The controller returns a purpose-built DTO that contains the identifiers needed by the frontend
 * (e.g. playerId, boardId and resume token). This is intentionally not a 1:1 mapping of the domain model to improve
 * developer experience and reduce the number of client-side API calls.
 */
@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyService lobbyService;
    private final GameResumeTokenRepository resumeTokenRepository;

    public LobbyController(LobbyService lobbyService,
                           GameResumeTokenRepository resumeTokenRepository) {
        this.lobbyService = lobbyService;
        this.resumeTokenRepository = resumeTokenRepository;
    }

    /**
     * Automatically joins the oldest open lobby or creates a new lobby if none exists.
     *
     * <p>The response includes:
     * <ul>
     *   <li>lobbyCode and gameCode</li>
     *   <li>the joining player's id</li>
     *   <li>the joining player's board state</li>
     *   <li>a player-specific resume token</li>
     * </ul>
     *
     * @param request request containing the username
     * @return lobby DTO including identifiers required by the frontend
     * @throws IllegalStateException if the joining player or board cannot be determined after matchmaking
     */
    @Operation(summary = "Automatically joins an open lobby or creates a new one")
    @PostMapping("/auto-join")
    public LobbyDto autoJoin(@RequestBody JoinLobbyRequest request) {
        String username = request.username();

        Lobby lobby = lobbyService.joinOrCreateLobby(username);
        Game game = lobby.getGame();

        // Identify the player that has just joined based on the submitted username.
        Player myPlayer = game.getPlayers().stream()
                .filter(p -> username.equals(p.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Joined player not found in game for username: " + username
                ));

        // Determine the board belonging to the joining player (by owner id).
        Board myBoard = game.getBoards().stream()
                .filter(b -> b.getOwner() != null && b.getOwner().getId().equals(myPlayer.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Board for player " + myPlayer.getId() + " not found in game " + game.getGameCode()
                ));

        // Ensure exactly one token per (game, player) pair.
        GameResumeToken tokenEntity = resumeTokenRepository
                .findByGame_IdAndPlayer_Id(game.getId(), myPlayer.getId())
                .orElseGet(() -> resumeTokenRepository.save(
                        new GameResumeToken(UUID.randomUUID().toString(), game, myPlayer)
                ));

        return LobbyDto.from(lobby, myPlayer, myBoard, tokenEntity.getToken());
    }
}
