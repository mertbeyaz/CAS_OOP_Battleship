package ch.battleship.battleshipbackend.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.repository.GameRepository;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import ch.battleship.battleshipbackend.web.api.dto.GameEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Listens to WebSocket lifecycle events and manages player connections in the database.
 *
 * <p>This component automatically detects when players connect or disconnect from games
 * and updates the game state accordingly. It uses Spring's WebSocket event system to
 * react to connection changes without requiring explicit client-side heartbeat polling.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Track player connections when they subscribe to game event topics</li>
 *   <li>Detect disconnections when WebSocket sessions close</li>
 *   <li>Move games to PAUSED status when a player disconnects</li>
 *   <li>Notify remaining players about connection status changes</li>
 *   <li>Track reconnections for resume flows</li>
 * </ul>
 *
 * <p>Integration with resume flow:
 * When a player disconnects, the game is moved to PAUSED status. Both players must then
 * use their resume tokens to rejoin the game. This listener detects when players reconnect
 * via subscription events, but does not automatically resume games - that requires explicit
 * resume token validation via the GameService.
 *
 * <p><b>Status Transitions on Disconnect:</b>
 * <ul>
 *   <li>RUNNING → PAUSED (requires resume tokens to continue)</li>
 *   <li>SETUP → PAUSED (requires resume tokens to continue setup)</li>
 *   <li>PAUSED → PAUSED (already paused, no change)</li>
 *   <li>WAITING → remains WAITING (not started yet)</li>
 *   <li>FINISHED → remains FINISHED (game over)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    /**
     * Grace period in milliseconds before pausing the game after a disconnect.
     * This allows for quick reconnects (e.g., browser refresh) without pausing the game.
     *
     * <p>Typical scenarios covered:
     * <ul>
     *   <li>Browser refresh: 2-5 seconds</li>
     *   <li>WiFi switch: 5-15 seconds</li>
     *   <li>Brief network hiccup: < 15 seconds</li>
     * </ul>
     */
    private static final long DISCONNECT_GRACE_PERIOD_MS = 15000; // 15 seconds

    private final PlayerConnectionRepository connectionRepository;
    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;

    /**
     * Handles player subscription to game events.
     *
     * <p>This method is triggered when a client subscribes to a WebSocket topic.
     * For game event subscriptions ({@code /topic/games/{gameCode}/events}), it extracts
     * the player ID from the subscription headers and registers or updates the connection
     * in the database.
     *
     * <p>Expected header: {@code playerId} containing the player's UUID as a string.
     *
     * @param event the subscription event containing session and destination information
     */
    @EventListener
    @Transactional
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();

        // Skip if essential information is missing
        if (sessionId == null || destination == null) {
            return;
        }

        // Only process game event subscriptions
        // Format: /topic/games/{gameCode}/events
        if (destination.startsWith("/topic/games/") && destination.endsWith("/events")) {
            String gameCode = extractGameCode(destination);

            // Extract player ID from subscription headers
            // The frontend must include this header when subscribing
            List<String> playerIdHeaders = accessor.getNativeHeader("playerId");
            if (playerIdHeaders == null || playerIdHeaders.isEmpty()) {
                log.warn("Subscription to {} without playerId header", destination);
                return;
            }

            try {
                UUID playerId = UUID.fromString(playerIdHeaders.get(0));
                registerConnection(gameCode, playerId, sessionId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid playerId in subscription header: {}", playerIdHeaders.get(0));
            }
        }
    }

    /**
     * Handles WebSocket disconnection events.
     *
     * <p>This method is automatically triggered by Spring when a WebSocket session closes,
     * which can happen due to:
     * <ul>
     *   <li>Browser/tab being closed</li>
     *   <li>Network connection loss</li>
     *   <li>Explicit client disconnect</li>
     *   <li>Session timeout</li>
     * </ul>
     *
     * <p>When a disconnect is detected, the game is moved to PAUSED status and both
     * players are notified. Both players will then need to use their resume tokens
     * to rejoin the game.
     *
     * <p><b>IMPORTANT:</b> Game is moved to PAUSED (not WAITING) to enable resume flow:
     * <ul>
     *   <li>PAUSED allows resume via resume tokens</li>
     *   <li>First resume: PAUSED → WAITING (handshake initiated)</li>
     *   <li>Second resume: WAITING → RUNNING (both players ready)</li>
     * </ul>
     *
     * @param event the disconnect event containing the session ID
     */
    @EventListener
    @Transactional
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (sessionId == null) {
            return;
        }

        // Find the player connection associated with this session
        Optional<PlayerConnection> connectionOpt = connectionRepository.findBySessionId(sessionId);
        if (connectionOpt.isEmpty()) {
            log.debug("Disconnect for untracked session: {}", sessionId);
            return;
        }

        PlayerConnection connection = connectionOpt.get();
        Game game = connection.getGame();
        Player player = connection.getPlayer();

        log.info("Player {} disconnected from game {} (session: {})",
                player.getUsername(), game.getGameCode(), sessionId);

        // Persist the disconnection in the database
        connection.markDisconnected();
        connectionRepository.save(connection);

        // Notify all players about the disconnection (warning only, not yet paused)
        sendEvent(game, GameEventDto.playerDisconnected(game, player));

        // Schedule game pause after grace period
        // This allows for quick reconnects (browser refresh, WiFi switch) without pausing
        UUID connectionId = connection.getId();
        Instant scheduledTime = Instant.now().plus(Duration.ofMillis(DISCONNECT_GRACE_PERIOD_MS));

        taskScheduler.schedule(
                () -> checkAndPauseIfStillDisconnected(connectionId),
                scheduledTime
        );

        log.debug("Scheduled pause check for game {} in {}ms (player: {})",
                game.getGameCode(), DISCONNECT_GRACE_PERIOD_MS, player.getUsername());
    }

    /**
     * Checks if a player connection is still disconnected after the grace period
     * and pauses the game if necessary.
     *
     * <p>This method is called asynchronously after the grace period expires.
     * If the player has reconnected in the meantime, no action is taken.
     *
     * <p><b>Note on transaction handling:</b>
     * This method runs in a separate thread scheduled by TaskScheduler.
     * We use a custom repository query with JOIN FETCH to eagerly load
     * player and game associations, preventing LazyInitializationException.
     *
     * @param connectionId ID of the player connection to check
     */
    @Transactional
    protected void checkAndPauseIfStillDisconnected(UUID connectionId) {
        // Refresh connection from database with player and game eagerly loaded
        // This prevents LazyInitializationException in the async context
        Optional<PlayerConnection> connectionOpt = connectionRepository.findByIdWithPlayerAndGame(connectionId);

        if (connectionOpt.isEmpty()) {
            log.debug("Connection {} no longer exists, skipping pause check", connectionId);
            return;
        }

        PlayerConnection connection = connectionOpt.get();

        // If player reconnected during grace period, do nothing
        if (connection.isConnected()) {
            log.info("Player {} reconnected during grace period, game {} continues",
                    connection.getPlayer().getUsername(), connection.getGame().getGameCode());
            return;
        }

        // Still disconnected after grace period → pause game
        Game game = connection.getGame();
        Player player = connection.getPlayer();

        // Auto-pause the game if it's in an active state
        // IMPORTANT: Move to PAUSED (not WAITING) for resume flow to work
        // FINISHED games are not affected by disconnections
        if (game.getStatus() == GameStatus.RUNNING ||
                game.getStatus() == GameStatus.SETUP) {

            log.info("Moving game {} to PAUSED status due to player {} still disconnected after {}ms grace period",
                    game.getGameCode(), player.getUsername(), DISCONNECT_GRACE_PERIOD_MS);

            // Move to PAUSED status - enables resume flow
            game.setStatus(GameStatus.PAUSED);
            gameRepository.save(game);

            // Send pause event to notify clients
            sendEvent(game, GameEventDto.gamePaused(game, player));
        }
        // If already PAUSED, no status change needed
        else if (game.getStatus() == GameStatus.PAUSED) {
            log.debug("Game {} already PAUSED, no status change needed", game.getGameCode());
        }
    }

    /**
     * Registers or updates a player connection in the database.
     *
     * <p>If the player already has a connection record for this game, it's updated
     * with the new session ID and marked as connected. If the player was previously
     * disconnected, a reconnection event is sent to all players.
     *
     * <p>If this is the first time the player connects to this game, a new
     * connection record is created.
     *
     * @param gameCode the public game identifier
     * @param playerId the player's UUID
     * @param sessionId the WebSocket session identifier
     */
    private void registerConnection(String gameCode, UUID playerId, String sessionId) {
        // Load game from database
        Game game = gameRepository.findByGameCode(gameCode).orElse(null);
        if (game == null) {
            log.warn("Connection registration for unknown game: {}", gameCode);
            return;
        }

        // Find the player in the game's player list
        Player player = game.getPlayers().stream()
                .filter(p -> p != null && p.getId() != null)
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);

        if (player == null) {
            log.warn("Connection registration for unknown player {} in game {}", playerId, gameCode);
            return;
        }

        // Check if player already has a connection record for this game
        Optional<PlayerConnection> existingConn = connectionRepository.findByGameAndPlayer(game, player);

        if (existingConn.isPresent()) {
            // Update existing connection record
            PlayerConnection conn = existingConn.get();
            boolean wasDisconnected = !conn.isConnected();

            // Mark as reconnected with new session ID
            conn.markReconnected(sessionId);
            connectionRepository.save(conn);

            log.info("Player {} {} to game {}",
                    player.getUsername(),
                    wasDisconnected ? "reconnected" : "updated connection",
                    gameCode);

            // If player was previously disconnected, notify all players
            if (wasDisconnected) {
                sendEvent(game, GameEventDto.playerReconnected(game, player));
            }
        } else {
            // Create new connection record for first-time connection
            PlayerConnection newConn = new PlayerConnection(game, player, sessionId);
            connectionRepository.save(newConn);

            log.info("New connection registered for player {} in game {}",
                    player.getUsername(), gameCode);
        }
    }

    /**
     * Sends an event to all clients subscribed to the game's event topic.
     *
     * <p>Events are published to {@code /topic/games/{gameCode}/events} and are
     * automatically received by all connected clients watching this game.
     *
     * @param game the game to send the event for
     * @param event the event DTO to send
     */
    private void sendEvent(Game game, GameEventDto event) {
        if (messagingTemplate != null) {
            String destination = "/topic/games/" + game.getGameCode() + "/events";
            messagingTemplate.convertAndSend(destination, event);
            log.debug("Sent {} event for game {}", event.type(), game.getGameCode());
        }
    }

    /**
     * Extracts the game code from a WebSocket destination path.
     *
     * <p>Expected format: {@code /topic/games/{gameCode}/events}
     * <p>Returns the game code part ({@code {gameCode}}).
     *
     * @param destination the full destination path
     * @return the extracted game code, or {@code null} if the format is invalid
     */
    private String extractGameCode(String destination) {
        // Split by "/" and extract the game code at index 3
        // Format: [empty, "topic", "games", "{gameCode}", "events"]
        String[] parts = destination.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }
}