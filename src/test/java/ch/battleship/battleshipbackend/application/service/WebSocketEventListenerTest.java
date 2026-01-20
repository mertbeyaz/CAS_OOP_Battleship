package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import ch.battleship.battleshipbackend.service.WebSocketEventListener;
import ch.battleship.battleshipbackend.web.api.dto.GameEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebSocketEventListener}.
 *
 * <p>Focus areas:
 * <ul>
 *   <li>Player connection registration when subscribing to game events</li>
 *   <li>Disconnect detection with grace period and automatic game pause</li>
 *   <li>Reconnection detection and event publishing</li>
 *   <li>Game status transitions on disconnect (RUNNING/SETUP → PAUSED)</li>
 *   <li>Event publishing to correct WebSocket topics</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Uses Mockito to simulate WebSocket events and repository interactions</li>
 *   <li>TaskScheduler is mocked to execute grace period tasks immediately for testing</li>
 *   <li>StompHeaderAccessor is mocked to simulate subscription headers</li>
 *   <li>IDs are simulated via {@link ch.battleship.battleshipbackend.testutil.EntityTestUtils#setId}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private PlayerConnectionRepository connectionRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private WebSocketEventListener listener;

    /**
     * Sets up TaskScheduler mock to execute scheduled tasks immediately.
     * This simulates the grace period expiring instantly for testing purposes.
     *
     * Uses lenient() because not all tests need the TaskScheduler (e.g., subscribe tests).
     */
    @BeforeEach
    void setUp() {
        // Mock TaskScheduler to execute tasks immediately instead of waiting for grace period
        // lenient() allows this mock to be unused in some tests without causing UnnecessaryStubbingException
        lenient().when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run(); // Execute immediately for testing
                    return null;
                });
    }

    // ------------------------------------------------------------------------------------
    // handleSubscribe - Connection Registration
    // ------------------------------------------------------------------------------------

    @Test
    void handleSubscribe_shouldRegisterNewConnection_whenPlayerSubscribesToGameEvents() {
        // Arrange
        String gameCode = "ABC123";
        String sessionId = "session-001";
        UUID playerId = UUID.randomUUID();

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, playerId);
        game.addPlayer(player);

        SessionSubscribeEvent event = createSubscribeEvent(
                sessionId,
                "/topic/games/" + gameCode + "/events",
                playerId.toString()
        );

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGameAndPlayer(game, player)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(PlayerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<PlayerConnection> connectionCaptor = ArgumentCaptor.forClass(PlayerConnection.class);

        // Act
        listener.handleSubscribe(event);

        // Assert: new connection should be created
        verify(connectionRepository, times(1)).save(connectionCaptor.capture());
        PlayerConnection savedConnection = connectionCaptor.getValue();

        assertThat(savedConnection.getGame()).isEqualTo(game);
        assertThat(savedConnection.getPlayer()).isEqualTo(player);
        assertThat(savedConnection.getSessionId()).isEqualTo(sessionId);
        assertThat(savedConnection.isConnected()).isTrue();

        // Assert: no event should be sent for first-time connection
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(GameEventDto.class));
    }

    @Test
    void handleSubscribe_shouldUpdateExistingConnection_whenPlayerReconnects() {
        // Arrange
        String gameCode = "ABC123";
        String oldSessionId = "session-old";
        String newSessionId = "session-new";
        UUID playerId = UUID.randomUUID();

        Game game = new Game("ABC123", GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, playerId);
        game.addPlayer(player);

        PlayerConnection existingConnection = new PlayerConnection(game, player, oldSessionId);
        existingConnection.markDisconnected(); // Simulate previous disconnect

        SessionSubscribeEvent event = createSubscribeEvent(
                newSessionId,
                "/topic/games/" + gameCode + "/events",
                playerId.toString()
        );

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGameAndPlayer(game, player))
                .thenReturn(Optional.of(existingConnection));
        when(connectionRepository.save(any(PlayerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<PlayerConnection> connectionCaptor = ArgumentCaptor.forClass(PlayerConnection.class);
        ArgumentCaptor<GameEventDto> eventCaptor = ArgumentCaptor.forClass(GameEventDto.class);

        // Act
        listener.handleSubscribe(event);

        // Assert: connection should be updated
        verify(connectionRepository, times(1)).save(connectionCaptor.capture());
        PlayerConnection updatedConnection = connectionCaptor.getValue();

        assertThat(updatedConnection.getSessionId()).isEqualTo(newSessionId);
        assertThat(updatedConnection.isConnected()).isTrue();

        // Assert: reconnection event should be sent
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/games/" + gameCode + "/events"), eventCaptor.capture());

        GameEventDto sentEvent = eventCaptor.getValue();
        assertThat(sentEvent.type().toString()).isEqualTo("PLAYER_RECONNECTED");
        assertThat(sentEvent.gameCode()).isEqualTo(gameCode);
    }

    @Test
    void handleSubscribe_shouldIgnoreSubscription_whenPlayerIdHeaderIsMissing() {
        // Arrange
        String sessionId = "session-001";

        SessionSubscribeEvent event = createSubscribeEvent(
                sessionId,
                "/topic/games/ABC123/events",
                null // No playerId header
        );

        // Act
        listener.handleSubscribe(event);

        // Assert: no repository interactions should occur
        verify(gameRepository, never()).findByGameCode(anyString());
        verify(connectionRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(GameEventDto.class));
    }

    @Test
    void handleSubscribe_shouldIgnoreSubscription_whenGameNotFound() {
        // Arrange
        String gameCode = "INVALID";
        String sessionId = "session-001";
        UUID playerId = UUID.randomUUID();

        SessionSubscribeEvent event = createSubscribeEvent(
                sessionId,
                "/topic/games/" + gameCode + "/events",
                playerId.toString()
        );

        when(gameRepository.findByGameCode(gameCode)).thenReturn(Optional.empty());

        // Act
        listener.handleSubscribe(event);

        // Assert: no connection should be saved
        verify(connectionRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(GameEventDto.class));
    }

    // ------------------------------------------------------------------------------------
    // handleDisconnect - Disconnect Detection with Grace Period
    // ------------------------------------------------------------------------------------

    @Test
    void handleDisconnect_shouldMarkConnectionAsDisconnected_andPauseRunningGame() {
        // Arrange
        String sessionId = "session-001";
        String gameCode = "ABC123";
        UUID playerId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        Game game = new Game("ABC123", GameConfiguration.defaultConfig());
        game.setStatus(GameStatus.RUNNING);
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, playerId);
        game.addPlayer(player);

        PlayerConnection connection = new PlayerConnection(game, player, sessionId);
        setId(connection, connectionId);

        SessionDisconnectEvent event = createDisconnectEvent(sessionId);

        when(connectionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(connection));
        when(connectionRepository.findByIdWithPlayerAndGame(connectionId))
                .thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(PlayerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gameRepository.save(any(Game.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<PlayerConnection> connectionCaptor = ArgumentCaptor.forClass(PlayerConnection.class);
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        ArgumentCaptor<GameEventDto> eventCaptor = ArgumentCaptor.forClass(GameEventDto.class);

        // Act
        listener.handleDisconnect(event);

        // Assert: connection should be marked as disconnected
        verify(connectionRepository, times(1)).save(connectionCaptor.capture());
        assertThat(connectionCaptor.getValue().isConnected()).isFalse();

        // Assert: game should be moved to PAUSED status (after grace period)
        verify(gameRepository, times(1)).save(gameCaptor.capture());
        assertThat(gameCaptor.getValue().getStatus()).isEqualTo(GameStatus.PAUSED);

        // Assert: two events should be sent (disconnect + pause)
        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/games/" + gameCode + "/events"), eventCaptor.capture());

        List<GameEventDto> sentEvents = eventCaptor.getAllValues();
        assertThat(sentEvents.get(0).type().toString()).isEqualTo("PLAYER_DISCONNECTED");
        assertThat(sentEvents.get(1).type().toString()).isEqualTo("GAME_PAUSED");

        // Assert: TaskScheduler was used for grace period
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void handleDisconnect_shouldPauseGame_whenGameIsInSetupPhase() {
        // Arrange
        String sessionId = "session-001";
        String gameCode = "ABC123";
        UUID connectionId = UUID.randomUUID();

        Game game = new Game("ABC123", GameConfiguration.defaultConfig());
        game.setStatus(GameStatus.SETUP); // Game in setup phase
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, UUID.randomUUID());
        game.addPlayer(player);

        PlayerConnection connection = new PlayerConnection(game, player, sessionId);
        setId(connection, connectionId);

        SessionDisconnectEvent event = createDisconnectEvent(sessionId);

        when(connectionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(connection));
        when(connectionRepository.findByIdWithPlayerAndGame(connectionId))
                .thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(PlayerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gameRepository.save(any(Game.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);

        // Act
        listener.handleDisconnect(event);

        // Assert: game should be moved to PAUSED status even in SETUP phase
        verify(gameRepository, times(1)).save(gameCaptor.capture());
        assertThat(gameCaptor.getValue().getStatus()).isEqualTo(GameStatus.PAUSED);

        // Assert: TaskScheduler was used
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void handleDisconnect_shouldNotPauseGame_whenGameIsAlreadyFinished() {
        // Arrange
        String sessionId = "session-001";
        String gameCode = "ABC123";
        UUID connectionId = UUID.randomUUID();

        Game game = new Game("ABC123", GameConfiguration.defaultConfig());
        game.setStatus(GameStatus.FINISHED); // Game already finished
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, UUID.randomUUID());
        game.addPlayer(player);

        PlayerConnection connection = new PlayerConnection(game, player, sessionId);
        setId(connection, connectionId);

        SessionDisconnectEvent event = createDisconnectEvent(sessionId);

        when(connectionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(connection));
        when(connectionRepository.findByIdWithPlayerAndGame(connectionId))
                .thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(PlayerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<GameEventDto> eventCaptor = ArgumentCaptor.forClass(GameEventDto.class);

        // Act
        listener.handleDisconnect(event);

        // Assert: connection should still be marked as disconnected
        verify(connectionRepository, times(1)).save(any(PlayerConnection.class));

        // Assert: game status should NOT be changed (FINISHED games are immune)
        verify(gameRepository, never()).save(any(Game.class));

        // Assert: only disconnect event should be sent (no pause event)
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/games/" + gameCode + "/events"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().type().toString()).isEqualTo("PLAYER_DISCONNECTED");

        // Assert: TaskScheduler was still called, but checkAndPause did nothing
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void handleDisconnect_shouldNotPauseGame_whenPlayerReconnectsDuringGracePeriod() {
        // Arrange
        String sessionId = "session-001";
        String gameCode = "ABC123";
        UUID connectionId = UUID.randomUUID();

        Game game = new Game("ABC123", GameConfiguration.defaultConfig());
        game.setStatus(GameStatus.RUNNING);
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, UUID.randomUUID());
        game.addPlayer(player);

        PlayerConnection connection = new PlayerConnection(game, player, sessionId);
        setId(connection, connectionId);

        SessionDisconnectEvent event = createDisconnectEvent(sessionId);

        when(connectionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(connection));

        // When checkAndPause runs, connection is already reconnected!
        PlayerConnection reconnectedConnection = new PlayerConnection(game, player, "session-002");
        setId(reconnectedConnection, connectionId);
        reconnectedConnection.markReconnected("session-002"); // connected = true

        when(connectionRepository.findByIdWithPlayerAndGame(connectionId))
                .thenReturn(Optional.of(reconnectedConnection));

        when(connectionRepository.save(any(PlayerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<GameEventDto> eventCaptor = ArgumentCaptor.forClass(GameEventDto.class);

        // Act
        listener.handleDisconnect(event);

        // Assert: connection was initially marked as disconnected
        verify(connectionRepository, times(1)).save(any(PlayerConnection.class));

        // Assert: game should NOT be paused (player reconnected during grace period!)
        verify(gameRepository, never()).save(any(Game.class));

        // Assert: only disconnect event sent, no pause event
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/games/" + gameCode + "/events"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().type().toString()).isEqualTo("PLAYER_DISCONNECTED");

        // Assert: TaskScheduler was called
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void handleDisconnect_shouldDoNothing_whenSessionNotTracked() {
        // Arrange
        String sessionId = "unknown-session";

        SessionDisconnectEvent event = createDisconnectEvent(sessionId);

        when(connectionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        // Act
        listener.handleDisconnect(event);

        // Assert: no actions should be taken
        verify(connectionRepository, never()).save(any());
        verify(gameRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(GameEventDto.class));
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    // ------------------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------------------

    /**
     * Creates a mock SessionSubscribeEvent with the given parameters.
     */
    private SessionSubscribeEvent createSubscribeEvent(String sessionId, String destination, String playerId) {
        SessionSubscribeEvent event = mock(SessionSubscribeEvent.class);
        org.springframework.messaging.Message message = mock(org.springframework.messaging.Message.class);

        when(event.getMessage()).thenReturn(message);

        // Create a StompHeaderAccessor with proper typing
        org.springframework.messaging.simp.SimpMessageHeaderAccessor accessor =
                StompHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);

        if (playerId != null) {
            accessor.setNativeHeader("playerId", playerId);
        }

        // Store accessor in message headers
        when(message.getHeaders()).thenReturn(accessor.getMessageHeaders());

        return event;
    }

    /**
     * Creates a mock SessionDisconnectEvent with the given session ID.
     */
    private SessionDisconnectEvent createDisconnectEvent(String sessionId) {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        org.springframework.messaging.Message message = mock(org.springframework.messaging.Message.class);

        when(event.getMessage()).thenReturn(message);

        // Create a StompHeaderAccessor with proper typing
        org.springframework.messaging.simp.SimpMessageHeaderAccessor accessor =
                StompHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.DISCONNECT);
        accessor.setSessionId(sessionId);

        // Store accessor in message headers
        when(message.getHeaders()).thenReturn(accessor.getMessageHeaders());

        return event;
    }
}