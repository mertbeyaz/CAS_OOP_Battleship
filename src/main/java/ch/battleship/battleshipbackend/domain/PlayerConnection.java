package ch.battleship.battleshipbackend.domain;

import ch.battleship.battleshipbackend.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks the connection state of a player within a game.
 *
 * <p>This entity is used to monitor WebSocket connections and detect when players
 * disconnect from active games. The connection state is persisted to the database
 * to survive server restarts and provide reliable disconnect detection.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Track active WebSocket sessions via {@code sessionId}</li>
 *   <li>Monitor connection status (connected/disconnected)</li>
 *   <li>Record last activity timestamp for timeout detection</li>
 * </ul>
 *
 * <p>The {@code uniqueConstraints} ensures that each player can only have one
 * connection record per game at a time.
 */
@Entity
@Table(name = "player_connections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "player_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerConnection extends BaseEntity {

    /**
     * The game this connection belongs to.
     * Uses lazy loading to avoid unnecessary database queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /**
     * The player who owns this connection.
     * Uses lazy loading to avoid unnecessary database queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /**
     * Timestamp of the last activity from this player.
     * Updated whenever the player subscribes to game events or reconnects.
     */
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    /**
     * Indicates whether the player is currently connected.
     * {@code true} if the WebSocket session is active, {@code false} otherwise.
     */
    @Column(name = "is_connected", nullable = false)
    private boolean connected;

    /**
     * The WebSocket session identifier.
     * Used to match disconnect events to specific player connections.
     * May be null if the player has never connected.
     */
    @Column(name = "session_id")
    private String sessionId;

    /**
     * Creates a new player connection record.
     *
     * <p>Initializes the connection as active (connected = true) and sets
     * the initial lastSeen timestamp to the current time.
     *
     * @param game the game this connection belongs to
     * @param player the player who owns this connection
     * @param sessionId the WebSocket session identifier
     */
    public PlayerConnection(Game game, Player player, String sessionId) {
        this.game = game;
        this.player = player;
        this.sessionId = sessionId;
        this.lastSeen = Instant.now();
        this.connected = true;
    }

    /**
     * Marks this connection as disconnected.
     *
     * <p>Called when the WebSocket session is closed (e.g., browser closed,
     * network issues, or explicit disconnect). Updates the lastSeen timestamp
     * to record when the disconnection occurred.
     */
    public void markDisconnected() {
        this.connected = false;
        this.lastSeen = Instant.now();
    }

    /**
     * Marks this connection as reconnected with a new session.
     *
     * <p>Called when a previously disconnected player subscribes to game events
     * again. Updates the session ID to the new WebSocket session and resets
     * the connection status to active.
     *
     * @param newSessionId the new WebSocket session identifier
     */
    public void markReconnected(String newSessionId) {
        this.connected = true;
        this.sessionId = newSessionId;
        this.lastSeen = Instant.now();
    }

    /**
     * Updates the last seen timestamp to the current time.
     *
     * <p>Can be used to track ongoing activity from the player without
     * changing the connection status.
     */
    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }
}