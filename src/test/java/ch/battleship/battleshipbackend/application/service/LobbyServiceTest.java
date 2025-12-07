package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.repository.LobbyRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.service.LobbyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
