// src/main/java/ch/battleship/battleshipbackend/service/LobbyService.java
package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.LobbyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class LobbyService {

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final GameService gameService;

    public LobbyService(LobbyRepository lobbyRepository,
                        GameRepository gameRepository,
                        GameService gameService) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.gameService = gameService;
    }

    /**
     * Versucht erst eine offene Lobby zu finden.
     * Wenn keine vorhanden, wird eine neue Lobby + Game erstellt.
     */
    public Lobby joinOrCreateLobby(String username) {
        // 1) offene Lobby suchen
        Optional<Lobby> maybeOpen =
                lobbyRepository.findFirstByStatusOrderByCreatedAtAsc(LobbyStatus.WAITING);

        if (maybeOpen.isPresent()) {
            Lobby lobby = maybeOpen.get();

            // vorhandenes Game joinen (Board + Flotte werden hier erzeugt, falls noch nicht)
            Game game = gameService.joinGame(lobby.getGame().getGameCode(), username);

            // nach dem Join: wenn 2 Spieler => Lobby FULL
            if (game.getPlayers().size() >= 2) {
                lobby.setStatus(LobbyStatus.FULL);
                lobbyRepository.save(lobby); // persistierten Status
            }

            return lobby;
        }

        // 2) keine Lobby gefunden -> neues Game + erster Spieler + Board + Flotte
        Game game = gameService.createGameAndJoinFirstPlayer(username);

        Lobby lobby = new Lobby(game); // Konstruktor generiert lobbyCode + WAITING
        return lobbyRepository.save(lobby);
    }
}
