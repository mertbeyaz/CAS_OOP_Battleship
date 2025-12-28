
package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.Game;

import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.enums.LobbyEventType;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.LobbyRepository;
import ch.battleship.battleshipbackend.web.api.dto.LobbyEventDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class LobbyService {

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public LobbyService(LobbyRepository lobbyRepository,
                        GameRepository gameRepository,
                        GameService gameService,
                        SimpMessagingTemplate messagingTemplate) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
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
                lobbyRepository.save(lobby);

                var event = new LobbyEventDto(
                        LobbyEventType.LOBBY_FULL,
                        lobby.getLobbyCode(),
                        game.getGameCode(),
                        lobby.getStatus().name(),
                        username
                );

                String destination = "/topic/lobbies/" + lobby.getLobbyCode() + "/events";
                messagingTemplate.convertAndSend(destination, event);

            }

            return lobby;
        }

        // 2) keine Lobby gefunden -> neues Game + erster Spieler + Board + Flotte
        Game game = gameService.createGameAndJoinFirstPlayer(username);

        Lobby lobby = new Lobby(game); // Konstruktor generiert lobbyCode + WAITING
        return lobbyRepository.save(lobby);
    }
}
