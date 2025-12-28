package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.LobbyEventType;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LobbyServiceTest {

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private GameService gameService;

    @InjectMocks
    private LobbyService lobbyService;

    @Mock
    SimpMessagingTemplate messagingTemplate;

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

        // joinGame fügt Player2 hinzu und gibt das aktualisierte Game zurück
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

    @Test
    void joinOrCreateLobby_whenSecondPlayerJoins_shouldMarkLobbyFullAndNotify() {
        // Arrange
        String lobbyCode = "TEST-LOBBY";
        String gameCode = "TEST-GAME";
        String joiningUsername = "Player2";

        GameConfiguration config = GameConfiguration.defaultConfig();
        Game game = new Game(gameCode, config);
        game.addPlayer(new Player("Player1"));
        game.addPlayer(new Player(joiningUsername));

        Lobby lobby = new Lobby(lobbyCode, game);
        lobby.setStatus(LobbyStatus.WAITING);

        when(lobbyRepository.findFirstByStatusOrderByCreatedAtAsc(LobbyStatus.WAITING))
                .thenReturn(Optional.of(lobby));

        when(gameService.joinGame(gameCode, joiningUsername)).thenReturn(game);

        when(lobbyRepository.save(any(Lobby.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Lobby result = lobbyService.joinOrCreateLobby(joiningUsername);

        // Assert
        assertThat(result.getStatus()).isEqualTo(LobbyStatus.FULL);
        verify(lobbyRepository).save(lobby);

        String destination = "/topic/lobbies/" + lobbyCode + "/events";

        verify(messagingTemplate).convertAndSend(eq(destination), argThat(matchesLobbyFullEvent(
                lobbyCode, gameCode, joiningUsername
        )));

        verifyNoMoreInteractions(messagingTemplate);
    }

    private static ArgumentMatcher<LobbyEventDto> matchesLobbyFullEvent(
            String lobbyCode, String gameCode, String joiningUsername
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
