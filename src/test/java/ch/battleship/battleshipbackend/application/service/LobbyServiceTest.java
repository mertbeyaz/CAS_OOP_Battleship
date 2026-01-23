package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.LobbyEventType;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.repository.LobbyRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.service.LobbyService;
import ch.battleship.battleshipbackend.web.api.dto.LobbyEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LobbyService}.
 *
 * <p><b>Scope:</b>
 * <ul>
 *   <li>Business behavior of "auto-join": join the oldest {@link LobbyStatus#WAITING} lobby if present,
 *       otherwise create a new lobby + game.</li>
 *   <li>State transitions on lobby level: WAITING -> FULL when the second player joins.</li>
 *   <li>WebSocket notifications: when lobby becomes FULL, a {@link LobbyEventType#LOBBY_FULL} event is
 *       published to the lobby-specific topic.</li>
 * </ul>
 *
 * <p><b>Collaborators mocked:</b>
 * <ul>
 *   <li>{@link LobbyRepository} for persistence / lookup</li>
 *   <li>{@link GameService} for joining/creating games</li>
 *   <li>{@link SimpMessagingTemplate} for WS broadcasting</li>
 * </ul>
 *
 * <p><b>Notes:</b>
 * <ul>
 *   <li>These tests intentionally do not assert on internal DB IDs. They focus on
 *       state transitions and outbound events.</li>
 *   <li>For WebSocket events we use a matcher to validate the DTO contents, while still allowing
 *       the controller/service to create the DTO instance internally.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LobbyServiceTest {

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private GameService gameService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private LobbyService lobbyService;

    /**
     * Scenario: A WAITING lobby exists.
     * When a second player joins via {@link LobbyService#joinOrCreateLobby(String)},
     * the lobby must become FULL and be saved.
     *
     * <p>This test verifies:</p>
     * <ul>
     *   <li>Lookup of an existing WAITING lobby.</li>
     *   <li>Delegation to {@link GameService#joinGame(String, String)}.</li>
     *   <li>Lobby state update to {@link LobbyStatus#FULL} when the game has 2 players.</li>
     * </ul>
     *
     * <p><b>Note:</b> The WS notification is validated more explicitly in
     * {@link #joinOrCreateLobby_whenSecondPlayerJoins_shouldMarkLobbyFullAndNotify()}.</p>
     */
    @Test
    void joinOrCreateLobby_shouldJoinExistingWaitingLobby_andMarkFullWhenTwoPlayers() {
        // Arrange
        String username = "Player2";

        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);

        Player player1 = new Player("Player1");
        game.addPlayer(player1);

        Lobby lobby = new Lobby(game);
        lobby.setStatus(LobbyStatus.WAITING);

        when(lobbyRepository.findFirstByStatusOrderByCreatedAtAsc(LobbyStatus.WAITING))
                .thenReturn(Optional.of(lobby));

        // Simulate that joinGame returns a game that now contains two players
        Player player2 = new Player(username);
        game.addPlayer(player2);

        when(gameService.joinGame(game.getGameCode(), username)).thenReturn(game);

        // Act
        Lobby result = lobbyService.joinOrCreateLobby(username);

        // Assert
        assertThat(result).isSameAs(lobby);
        assertThat(result.getStatus()).isEqualTo(LobbyStatus.FULL);

        verify(lobbyRepository).save(lobby);
        verify(gameService).joinGame(game.getGameCode(), username);
        verifyNoMoreInteractions(gameService);
    }

    /**
     * Scenario: No WAITING lobby exists.
     * When a user calls {@link LobbyService#joinOrCreateLobby(String)},
     * the service must create a new game and a new lobby, and persist the lobby.
     */
    @Test
    void joinOrCreateLobby_shouldCreateNewLobbyAndGameWhenNoneExists() {
        // Arrange
        String username = "Player1";

        when(lobbyRepository.findFirstByStatusOrderByCreatedAtAsc(LobbyStatus.WAITING))
                .thenReturn(Optional.empty());

        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(config);

        Player player = new Player(username);
        game.addPlayer(player);

        when(gameService.createGameAndJoinFirstPlayer(username)).thenReturn(game);

        Lobby persistedLobby = mock(Lobby.class);
        when(lobbyRepository.save(any(Lobby.class))).thenReturn(persistedLobby);

        // Act
        Lobby result = lobbyService.joinOrCreateLobby(username);

        // Assert
        assertThat(result).isSameAs(persistedLobby);

        verify(gameService).createGameAndJoinFirstPlayer(username);
        verify(lobbyRepository).save(any(Lobby.class));
    }

    /**
     * Scenario: A second player joins an existing WAITING lobby.
     * The lobby must become FULL and WebSocket events must be published:
     * <ul>
     *   <li>PLAYER_JOINED to /topic/games/{gameCode}/events (from GameService)</li>
     *   <li>LOBBY_FULL to /topic/lobbies/{lobbyCode}/events (from LobbyService)</li>
     * </ul>
     *
     * <p>The LOBBY_FULL event must contain:</p>
     * <ul>
     *   <li>lobbyCode</li>
     *   <li>gameCode</li>
     *   <li>status = "FULL"</li>
     *   <li>joinedUsername = joiningUsername</li>
     * </ul>
     */
    @Test
    void joinOrCreateLobby_whenSecondPlayerJoins_shouldMarkLobbyFullAndNotify() {
        // Arrange
        String lobbyCode = "TEST-LOBBY";
        String gameCode = "TEST-GAME";
        String joiningUsername = "Player2";

        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(gameCode, config);

        // Game already has 2 players after joinGame(...) (service uses players.size() >= 2)
        game.addPlayer(new Player("Player1"));
        game.addPlayer(new Player(joiningUsername));

        Lobby lobby = new Lobby(lobbyCode, game);
        lobby.setStatus(LobbyStatus.WAITING);

        when(lobbyRepository.findFirstByStatusOrderByCreatedAtAsc(LobbyStatus.WAITING))
                .thenReturn(Optional.of(lobby));

        when(gameService.joinGame(gameCode, joiningUsername)).thenReturn(game);

        // Make repository save return the same lobby instance
        when(lobbyRepository.save(any(Lobby.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Lobby result = lobbyService.joinOrCreateLobby(joiningUsername);

        // Assert: lobby FULL persisted
        assertThat(result.getStatus()).isEqualTo(LobbyStatus.FULL);
        verify(lobbyRepository).save(lobby);

        // Assert: LOBBY_FULL event published (ignoring PLAYER_JOINED from GameService)
        String lobbyDestination = "/topic/lobbies/" + lobbyCode + "/events";

        verify(messagingTemplate).convertAndSend(
                eq(lobbyDestination),
                argThat(matchesLobbyFullEvent(lobbyCode, gameCode, joiningUsername))
        );

    }

    /**
     * Argument matcher to validate that a published {@link LobbyEventDto} represents a lobby-full event
     * for a given lobby/game and the joining user.
     *
     * @param lobbyCode        expected lobby code
     * @param gameCode         expected game code
     * @param joiningUsername  expected username of the player who triggered the FULL state
     * @return matcher for {@link LobbyEventDto}
     */
    private static ArgumentMatcher<LobbyEventDto> matchesLobbyFullEvent(
            String lobbyCode,
            String gameCode,
            String joiningUsername
    ) {
        return dto ->
                dto != null
                        && dto.type() == LobbyEventType.LOBBY_FULL
                        && lobbyCode.equals(dto.lobbyCode())
                        && gameCode.equals(dto.gameCode())
                        && "FULL".equals(dto.status())
                        && joiningUsername.equals(dto.joinedUsername());
    }
}