package ch.battleship.battleshipbackend.web.api.controller;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.service.LobbyService;
import ch.battleship.battleshipbackend.web.api.dto.JoinLobbyRequest;
import ch.battleship.battleshipbackend.web.api.dto.LobbyDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller providing lobby matchmaking endpoints.
 *
 * <p>The lobby layer implements a simple matchmaking strategy:
 * clients call {@code /auto-join} to either join an existing open lobby or create a new one.
 *
 * <p>DTO note:
 * The controller returns a purpose-built DTO that contains the identifiers needed by the frontend
 * (e.g. playerId and boardId). This is intentionally not a 1:1 mapping of the domain model to improve
 * developer experience and reduce the number of client-side API calls.
 */
@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyService lobbyService;

    /**
     * Creates a new {@code LobbyController}.
     *
     * @param lobbyService service implementing lobby join/create matchmaking logic
     */
    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    /**
     * Automatically joins the oldest open lobby or creates a new lobby if none exists.
     *
     * <p>The response includes:
     * <ul>
     *   <li>lobbyCode and gameCode</li>
     *   <li>the joining player's id</li>
     *   <li>the joining player's board id</li>
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

        // Provide playerId and boardId to the frontend to simplify further calls.
        return LobbyDto.from(lobby, myPlayer, myBoard);
    }
}
