package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.*;
import ch.battleship.battleshipbackend.domain.enums.LobbyStatus;
import ch.battleship.battleshipbackend.repository.GameResumeTokenRepository;
import ch.battleship.battleshipbackend.service.LobbyService;
import ch.battleship.battleshipbackend.web.api.controller.LobbyController;
import ch.battleship.battleshipbackend.web.api.dto.JoinLobbyRequest;
import ch.battleship.battleshipbackend.web.api.dto.LobbyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private GameResumeTokenRepository gameResumeTokenRepository;

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
        String username = "PlayerA";
        JoinLobbyRequest request = new JoinLobbyRequest(username);

        GameConfiguration config = GameConfiguration.defaultConfig();

        // Build a minimal domain state: Game -> Player -> Board, wrapped into Lobby
        Game game = new Game("TEST-GAME-CODE", config);
        UUID gameId = UUID.randomUUID();
        setId(game, gameId);

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

        // NEW: mock existing resume token so controller can map it
        GameResumeToken tokenEntity = new GameResumeToken("RESUME-TOKEN-123", game, player);
        setId(tokenEntity, UUID.randomUUID());

        when(gameResumeTokenRepository.findByGame_IdAndPlayer_Id(gameId, playerId))
                .thenReturn(Optional.of(tokenEntity));

        // Act
        LobbyDto dto = lobbyController.autoJoin(request);

        // Assert – verify mapping
        assertThat(dto.lobbyCode()).isEqualTo("TEST-LOBBY-CODE");
        assertThat(dto.gameCode()).isEqualTo("TEST-GAME-CODE");
        assertThat(dto.status()).isEqualTo("WAITING");

        // Important: the DTO must identify the joining player
        assertThat(dto.myPlayerId()).isEqualTo(playerId);
        assertThat(dto.myPlayerName()).isEqualTo(username);

        // NEW: token is now part of the contract
        assertThat(dto.resumeToken()).isEqualTo("RESUME-TOKEN-123");

        // Verify token lookup happened
        verify(gameResumeTokenRepository).findByGame_IdAndPlayer_Id(gameId, playerId);
        verify(gameResumeTokenRepository, never()).save(any(GameResumeToken.class));
    }

    /**
     * Ensures that {@link LobbyController#autoJoin(JoinLobbyRequest)} returns a {@link LobbyDto}
     * containing an existing resume token for the joining player.
     *
     * <p>Given: {@link LobbyService} returns a {@link Lobby} with a {@link Game} that contains the joining
     * {@link Player} and the player's {@link Board}. Additionally, {@link GameResumeTokenRepository}
     * returns an existing {@link GameResumeToken} for the (game, player) pair.</p>
     *
     * <p>Then: The controller maps the domain model into {@link LobbyDto} and exposes:
     * lobbyCode, gameCode, status, {@code myPlayerId} and the existing {@code resumeToken}.</p>
     *
     * <p>And: No new token is created, i.e. {@link GameResumeTokenRepository#save(Object)} is never called.</p>
     */
    @Test
    void autoJoin_shouldReturnExistingResumeToken_whenTokenAlreadyExists() {
        // Arrange
        String username = "PlayerA";
        JoinLobbyRequest request = new JoinLobbyRequest(username);

        GameConfiguration config = GameConfiguration.defaultConfig();

        Game game = new Game("TEST-GAME-CODE", config);
        UUID gameId = UUID.randomUUID();
        setId(game, gameId);

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

        // Existing token in DB for this (game, player)
        String existingTokenValue = "resume-token-existing-123";
        GameResumeToken existingToken = new GameResumeToken(existingTokenValue, game, player);
        setId(existingToken, UUID.randomUUID());

        when(gameResumeTokenRepository.findByGame_IdAndPlayer_Id(gameId, playerId))
                .thenReturn(Optional.of(existingToken));

        // Act
        LobbyDto dto = lobbyController.autoJoin(request);

        // Assert
        assertThat(dto.lobbyCode()).isEqualTo("TEST-LOBBY-CODE");
        assertThat(dto.gameCode()).isEqualTo("TEST-GAME-CODE");
        assertThat(dto.myPlayerId()).isEqualTo(playerId);

        // Resume token must be the already existing one
        // IMPORTANT: adjust accessor name if your DTO uses a different record component name
        assertThat(dto.resumeToken()).isEqualTo(existingTokenValue);

        // Verify repo interactions
        verify(gameResumeTokenRepository).findByGame_IdAndPlayer_Id(gameId, playerId);
        verify(gameResumeTokenRepository, never()).save(any(GameResumeToken.class));
    }

    /**
     * Ensures that {@link LobbyController#autoJoin(JoinLobbyRequest)} creates and returns a resume token
     * when no token exists yet for the joining player.
     *
     * <p>Given: {@link LobbyService} returns a {@link Lobby} with a {@link Game} that contains the joining
     * {@link Player} and the player's {@link Board}. {@link GameResumeTokenRepository} returns empty for
     * {@link GameResumeTokenRepository#findByGame_IdAndPlayer_Id(java.util.UUID, java.util.UUID)}.</p>
     *
     * <p>Then: The controller creates a new {@link GameResumeToken} (with a non-empty token value),
     * persists it via {@link GameResumeTokenRepository#save(Object)}, and returns it as {@code resumeToken}
     * within the {@link LobbyDto}.</p>
     */
    @Test
    void autoJoin_shouldCreateResumeToken_whenTokenDoesNotExistYet() {
        // Arrange
        String username = "PlayerA";
        JoinLobbyRequest request = new JoinLobbyRequest(username);

        GameConfiguration config = GameConfiguration.defaultConfig();

        Game game = new Game("TEST-GAME-CODE", config);
        UUID gameId = UUID.randomUUID();
        setId(game, gameId);

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

        // No token exists yet
        when(gameResumeTokenRepository.findByGame_IdAndPlayer_Id(gameId, playerId))
                .thenReturn(Optional.empty());

        // Capture what gets saved
        ArgumentCaptor<GameResumeToken> tokenCaptor = ArgumentCaptor.forClass(GameResumeToken.class);

        // Return the entity as if persisted (id not needed for this assertion, but can be set)
        when(gameResumeTokenRepository.save(any(GameResumeToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        LobbyDto dto = lobbyController.autoJoin(request);

        // Assert basic mapping
        assertThat(dto.lobbyCode()).isEqualTo("TEST-LOBBY-CODE");
        assertThat(dto.gameCode()).isEqualTo("TEST-GAME-CODE");
        assertThat(dto.myPlayerId()).isEqualTo(playerId);

        // Verify: save was called and token is non-empty
        verify(gameResumeTokenRepository).findByGame_IdAndPlayer_Id(gameId, playerId);
        verify(gameResumeTokenRepository).save(tokenCaptor.capture());

        GameResumeToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getGame()).isSameAs(game);
        assertThat(savedToken.getPlayer()).isSameAs(player);

        // Token should be generated (non-null, non-blank)
        assertThat(savedToken.getToken()).isNotNull();
        assertThat(savedToken.getToken()).isNotBlank();

        // Response must expose exactly that token
        // IMPORTANT: adjust accessor name if your DTO uses a different record component name
        assertThat(dto.resumeToken()).isEqualTo(savedToken.getToken());
    }
}
