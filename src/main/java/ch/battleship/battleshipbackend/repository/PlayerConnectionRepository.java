package ch.battleship.battleshipbackend.repository;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link PlayerConnection} entities.
 *
 * <p>Provides query methods to track and manage player WebSocket connections
 * across games. Used primarily by the WebSocket event listener to detect
 * disconnections and by the game service to check connection status.
 */
@Repository
public interface PlayerConnectionRepository extends JpaRepository<PlayerConnection, UUID> {

    /**
     * Finds a player connection by game and player.
     *
     * <p>Used to check if a player already has a connection record in a game,
     * or to update an existing connection when a player reconnects.
     *
     * @param game the game to search in
     * @param player the player to find
     * @return the player connection if found, empty otherwise
     */
    Optional<PlayerConnection> findByGameAndPlayer(Game game, Player player);

    /**
     * Finds a player connection by WebSocket session ID.
     *
     * <p>Used by the disconnect event listener to identify which player
     * disconnected when a WebSocket session closes.
     *
     * @param sessionId the WebSocket session identifier
     * @return the player connection if found, empty otherwise
     */
    Optional<PlayerConnection> findBySessionId(String sessionId);

    /**
     * Finds all player connections for a specific game.
     *
     * <p>Used to check the connection status of all players in a game,
     * for example to determine if all players are connected for auto-resume logic.
     *
     * @param game the game to search in
     * @return list of all player connections in the game (may be empty)
     */
    List<PlayerConnection> findByGame(Game game);

    /**
     * Deletes all player connections for a specific game.
     *
     * <p>Used for cleanup when a game ends or is deleted. This is a
     * modifying query and should be called within a transactional context.
     *
     * @param game the game whose connections should be deleted
     */
    void deleteByGame(Game game);
}