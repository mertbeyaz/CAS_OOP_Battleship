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

@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyService lobbyService;

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @Operation(summary = "Automatically joins an open lobby or creates a new one")
    @PostMapping("/auto-join")
    public LobbyDto autoJoin(@RequestBody JoinLobbyRequest request) {
        String username = request.username();

        Lobby lobby = lobbyService.joinOrCreateLobby(username);
        Game game = lobby.getGame();

        // 1) Den Player ermitteln, der eben gejoint hat
        Player myPlayer = game.getPlayers().stream()
                .filter(p -> username.equals(p.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Joined player not found in game for username: " + username
                ));

        // 2) Board über Player-Referenz/ID ermitteln (statt username)
        Board myBoard = game.getBoards().stream()
                .filter(b -> b.getOwner() != null && b.getOwner().getId().equals(myPlayer.getId()))
                // alternativ: b.getOwner().equals(myPlayer)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Board for player " + myPlayer.getId() + " not found in game " + game.getGameCode()
                ));

        // 3) Player-Id & Board-Id im DTO mitgeben
        return LobbyDto.fromEntity(lobby, myPlayer, myBoard);
    }
}

