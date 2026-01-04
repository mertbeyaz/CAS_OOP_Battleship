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

/**
 * Application service responsible for lobby matchmaking and lobby lifecycle updates.
 *
 * <p>Main responsibilities:
 * <ul>
 *   <li>Find an open lobby (WAITING) and join its game</li>
 *   <li>Create a new lobby and game if no open lobby exists</li>
 *   <li>Update lobby status (e.g. FULL) once enough players joined</li>
 *   <li>Publish lobby events to clients via WebSocket topics</li>
 * </ul>
 *
 * <p>Concurrency note:
 * Multiple users may try to join an open lobby at the same time. The Lobby entity uses
 * optimistic locking ({@code @Version}) to reduce race conditions when status changes.
 */
@Service
@Transactional
public class LobbyService {

    private final LobbyRepository lobbyRepository;

    /**
     * Currently not used directly in this service, but kept for future lobby/game persistence needs.
     */
    private final GameRepository gameRepository;

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates a new {@code LobbyService}.
     *
     * @param lobbyRepository repository for lobby persistence
     * @param gameRepository repository for game persistence
     * @param gameService service providing game join/create logic
     * @param messagingTemplate WebSocket messaging template for lobby event publishing
     */
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
     * Joins an existing open lobby or creates a new lobby if none is available.
     *
     * <p>Matchmaking strategy:
     * <ul>
     *   <li>Try to find the oldest lobby with status WAITING (FIFO).</li>
     *   <li>If found, join the associated game.</li>
     *   <li>If after joining the game has two players, mark lobby as FULL and publish a lobby event.</li>
     *   <li>If no open lobby exists, create a new game, join the first player and create a new lobby.</li>
     * </ul>
     *
     * @param username username of the player joining or creating a lobby
     * @return the lobby the player joined or created
     * @throws IllegalStateException if the underlying game cannot be joined due to invalid game state
     */
    public Lobby joinOrCreateLobby(String username) {
        // 1) Find the oldest open lobby (FIFO). If found, join its associated game.
        Optional<Lobby> maybeOpen =
                lobbyRepository.findFirstByStatusOrderByCreatedAtAsc(LobbyStatus.WAITING);

        if (maybeOpen.isPresent()) {
            Lobby lobby = maybeOpen.get();

            // Join the existing game (this also creates board + auto-placed fleet for the player).
            Game game = gameService.joinGame(lobby.getGame().getGameCode(), username);

            // If the second player joined, the lobby becomes FULL and clients are notified.
            if (game.getPlayers().size() >= 2) {
                lobby.setStatus(LobbyStatus.FULL);
                lobbyRepository.save(lobby);

                LobbyEventDto event = new LobbyEventDto(
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

        // 2) No open lobby found -> create new game and join first player (board + fleet are created there).
        Game game = gameService.createGameAndJoinFirstPlayer(username);

        // Create a new lobby (constructor generates lobbyCode and sets status WAITING).
        Lobby lobby = new Lobby(game);
        return lobbyRepository.save(lobby);
    }
}
