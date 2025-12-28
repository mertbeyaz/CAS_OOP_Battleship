package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.service.LobbyService;
import ch.battleship.battleshipbackend.web.api.controller.LobbyController;
import ch.battleship.battleshipbackend.web.api.dto.LobbyDto;
import ch.battleship.battleshipbackend.web.api.dto.JoinLobbyRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LobbyControllerTest {

    @Mock
    private LobbyService lobbyService;

    @InjectMocks
    private LobbyController lobbyController;

    private static void setId(Object entity, UUID id) {
        try {
            Field idField = null;
            Class<?> type = entity.getClass();
            // ggf. in Superklasse nach "id" suchen
            while (type != null && idField == null) {
                try {
                    idField = type.getDeclaredField("id");
                } catch (NoSuchFieldException ex) {
                    type = type.getSuperclass();
                }
            }
            if (idField == null) {
                throw new IllegalStateException("No id field found on " + entity.getClass());
            }
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void autoJoin_shouldReturnLobbyDtoWithMyId() {
        // Arrange
        String username = "mcoppola";
        JoinLobbyRequest request = new JoinLobbyRequest(username);

        GameConfiguration config = GameConfiguration.defaultConfig();

        // Game mit einem Player + Board aufbauen
        Game game = new Game("TEST-GAME-CODE", config);
        UUID gameId = UUID.randomUUID();
        setId(game, gameId);

        Player player = new Player(username);
        UUID playerId = UUID.randomUUID();
        setId(player, playerId);
        game.addPlayer(player);

        Board board = new Board(config.getBoardWidth(), config.getBoardHeight(), player);
        UUID boardId = UUID.randomUUID();
        setId(board, boardId);
        game.addBoard(board);

        Lobby lobby = new Lobby("TEST-LOBBY-CODE", game);
        lobby.setStatus(LobbyStatus.WAITING);
        UUID lobbyId = UUID.randomUUID();
        setId(lobby, lobbyId);

        when(lobbyService.joinOrCreateLobby(username)).thenReturn(lobby);

        // Act
        LobbyDto dto = lobbyController.autoJoin(request);

        // Assert – Top-Level-Felder
        assertThat(dto.lobbyCode()).isEqualTo("TEST-LOBBY-CODE");
        assertThat(dto.gameCode()).isEqualTo("TEST-GAME-CODE");
        assertThat(dto.status()).isEqualTo("WAITING");

        assertThat(dto.myPlayerId()).isEqualTo(playerId);


        // players[] prüfen
        /*
        assertThat(dto.players()).hasSize(1);
        LobbyDto.PlayerInfoDto p = dto.players().get(0);

        assertThat(p.id()).isEqualTo(playerId);
        assertThat(p.username()).isEqualTo("mcoppola");
        assertThat(p.boardId()).isEqualTo(boardId);
         */
    }
}
