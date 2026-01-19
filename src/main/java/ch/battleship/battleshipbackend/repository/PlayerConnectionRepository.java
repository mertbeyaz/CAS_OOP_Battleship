package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PlayerConnection} entities.
 *
 * <p>Provides queries for tracking and managing player WebSocket connections,
 * including session-based lookups and cleanup of stale connections.
 */
public interface PlayerConnectionRepository extends JpaRepository<PlayerConnection, UUID> {

    /**
     * Finds a player connection by WebSocket session ID.
     *
     * @param sessionId the WebSocket session identifier
     * @return connection if found, empty otherwise
     */
    Optional<PlayerConnection> findBySessionId(String sessionId);

    /**
     * Finds a player connection by game and player.
     *
     * @param game the game
     * @param player the player
     * @return connection if found, empty otherwise
     */
    Optional<PlayerConnection> findByGameAndPlayer(Game game, Player player);

    /**
     * Finds all connections for a specific game.
     *
     * @param game the game
     * @return list of connections (may be empty)
     */
    List<PlayerConnection> findByGame(Game game);

    /**
     * Finds all connections where lastSeen is before the given threshold.
     *
     * <p>Used by scheduled cleanup to remove stale connection records.
     *
     * @param threshold timestamp threshold (connections older than this will be returned)
     * @return list of old connections
     */
    List<PlayerConnection> findByLastSeenBefore(Instant threshold);

    /**
     * Finds a player connection by ID with player and game eagerly fetched.
     *
     * <p>This method is used by asynchronous tasks (e.g., disconnect grace period checks)
     * that run outside of the normal request transaction scope. Eager fetching prevents
     * LazyInitializationException when accessing player or game properties.
     *
     * @param id the connection ID
     * @return connection with player and game loaded, empty if not found
     */
    @Query("SELECT pc FROM PlayerConnection pc " +
            "JOIN FETCH pc.player " +
            "JOIN FETCH pc.game " +
            "WHERE pc.id = :id")
    Optional<PlayerConnection> findByIdWithPlayerAndGame(@Param("id") UUID id);
}