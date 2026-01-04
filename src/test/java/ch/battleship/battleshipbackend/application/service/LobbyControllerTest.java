package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.service.LobbyService;
import ch.battleship.battleshipbackend.web.api.controller.LobbyController;
import ch.battleship.battleshipbackend.web.api.dto.JoinLobbyRequest;
import ch.battleship.battleshipbackend.web.api.dto.LobbyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Controller-level unit tests for {@link LobbyController}.
 *
 * <p><b>Scope:</b>
 * <ul>
 *   <li>Verifies the REST adapter behavior of {@link LobbyController#autoJoin(JoinLobbyRequest)}.</li>
 *   <li>Ensures the controller maps the domain model (Lobby/Game/Player/Board) into {@link LobbyDto}
 *       correctly and exposes the expected public fields.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b>
 * <ul>
 *   <li>Lobby creation / join business rules (covered by {@link LobbyService} tests).</li>
 *   <li>Database persistence (repositories are not used here).</li>
 * </ul>
 *
 * <p>The controller expects persisted entities (IDs assigned by JPA).
 * In unit tests we simulate this by setting {@code id} fields via reflection.</p>
 */
@ExtendWith(MockitoExtension.class)
class LobbyControllerTest {

    @Mock
    private LobbyService lobbyService;

    @InjectMocks
    private LobbyController lobbyController;

    /**
     * Sets the {@code id} field on a JPA entity (or any object with an {@code id} field),
     * walking up the class hierarchy if needed.
     *
     * <p>Used to simulate "persisted" entities in unit tests, because the controller code
     * assumes IDs exist (e.g. playerId/boardId in DTO mapping).</p>
     *
     * @param entity The entity instance whose {@code id} should be set.
     * @param id     The UUID to assign.
     */
    private static void setId(Object entity, UUID id) {
        try {
            Field idField = null;
            Class<?> type = entity.getClass();

            // Search for field "id" in class or any superclass
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
            throw new RuntimeException("Failed to set id via reflection", e);
        }
    }

    /**
     * Ensures that {@link LobbyController#autoJoin(JoinLobbyRequest)} returns a {@link LobbyDto}
     * containing the lobby/game identifiers and the caller's playerId.
     *
     * <p>Given: LobbyService returns a Lobby with a Game that contains exactly one Player (the joining user)
     * and one Board owned by that Player.</p>
     *
     * <p>Then: Controller maps the domain model into {@link LobbyDto} and exposes:
     * lobbyCode, gameCode, status and {@code myPlayerId}.</p>
     */
    @Test
    void autoJoin_shouldReturnLobbyDtoWithMyId() {
        // Arrange
        String username = "mcoppola";
        JoinLobbyRequest request = new JoinLobbyRequest(username);

        GameConfiguration config = GameConfiguration.defaultConfig();

        // Build a minimal domain state: Game -> Player -> Board, wrapped into Lobby
        Game game = new Game("TEST-GAME-CODE", config);
        setId(game, UUID.randomUUID());

        Player player = new Player(username);
        UUID playerId = UUID.randomUUID();
        setId(player, playerId);
        game.addPlayer(player);

        Board board = new Board(config.getBoardWidth(), config.getBoardHeight(), player);
        setId(board, UUID.randomUUID());
        game.addBoard(board);

        Lobby lobby = new Lobby("TEST-LOBBY-CODE", game);
        lobby.setStatus(LobbyStatus.WAITING);
        setId(lobby, UUID.randomUUID());

        when(lobbyService.joinOrCreateLobby(username)).thenReturn(lobby);

        // Act
        LobbyDto dto = lobbyController.autoJoin(request);

        // Assert – verify mapping
        assertThat(dto.lobbyCode()).isEqualTo("TEST-LOBBY-CODE");
        assertThat(dto.gameCode()).isEqualTo("TEST-GAME-CODE");
        assertThat(dto.status()).isEqualTo("WAITING");

        // Important: the DTO must identify the joining player
        assertThat(dto.myPlayerId()).isEqualTo(playerId);

        // Note:
        // players[] assertions were removed because LobbyDto no longer exposes players[] in this version.
        // If you re-introduce players[] in the DTO, you can re-add those checks here.
    }
}
