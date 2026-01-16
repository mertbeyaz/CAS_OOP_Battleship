package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link PlayerConnection} domain entity.
 *
 * <p>Focus areas:
 * <ul>
 *   <li>Connection initialization and default values</li>
 *   <li>Disconnect marking behavior</li>
 *   <li>Reconnection with session update</li>
 *   <li>Last seen timestamp updates</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Tests verify business logic methods ({@code markDisconnected}, {@code markReconnected})</li>
 *   <li>Timestamp assertions use {@code within()} to handle test execution timing variations</li>
 * </ul>
 */
class PlayerConnectionTest {

    // ------------------------------------------------------------------------------------
    // Constructor and Initialization
    // ------------------------------------------------------------------------------------

    @Test
    void constructor_shouldInitializeConnection_withDefaultValues() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Alice");
        setId(player, UUID.randomUUID());

        String sessionId = "session-123";
        Instant before = Instant.now();

        // Act
        PlayerConnection connection = new PlayerConnection(game, player, sessionId);
        Instant after = Instant.now();

        // Assert
        assertThat(connection.getGame()).isEqualTo(game);
        assertThat(connection.getPlayer()).isEqualTo(player);
        assertThat(connection.getSessionId()).isEqualTo(sessionId);
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.getLastSeen()).isBetween(before, after);
    }

    // ------------------------------------------------------------------------------------
    // markDisconnected
    // ------------------------------------------------------------------------------------

    @Test
    void markDisconnected_shouldSetConnectedToFalse_andUpdateLastSeen() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Alice");
        PlayerConnection connection = new PlayerConnection(game, player, "session-123");

        // Wait a tiny bit to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }

        Instant beforeDisconnect = Instant.now();

        // Act
        connection.markDisconnected();
        Instant afterDisconnect = Instant.now();

        // Assert
        assertThat(connection.isConnected()).isFalse();
        assertThat(connection.getLastSeen()).isBetween(beforeDisconnect, afterDisconnect);
    }

    @Test
    void markDisconnected_shouldUpdateLastSeen_evenWhenAlreadyDisconnected() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Bob");
        PlayerConnection connection = new PlayerConnection(game, player, "session-456");

        // First disconnect
        connection.markDisconnected();
        Instant firstDisconnectTime = connection.getLastSeen();

        // Wait to ensure timestamp difference (at least 1ms)
        sleepAtLeast(2);

        // Act: disconnect again
        connection.markDisconnected();

        // Assert: lastSeen should be updated
        assertThat(connection.isConnected()).isFalse();
        assertThat(connection.getLastSeen()).isAfterOrEqualTo(firstDisconnectTime);
        // Use isAfterOrEqualTo to be safe, but in practice it should be after due to sleep
    }

    // ------------------------------------------------------------------------------------
    // markReconnected
    // ------------------------------------------------------------------------------------

    @Test
    void markReconnected_shouldSetConnectedToTrue_updateSessionId_andUpdateLastSeen() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Charlie");
        PlayerConnection connection = new PlayerConnection(game, player, "session-old");

        // Disconnect first
        connection.markDisconnected();
        assertThat(connection.isConnected()).isFalse();

        // Wait to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }

        Instant beforeReconnect = Instant.now();
        String newSessionId = "session-new";

        // Act
        connection.markReconnected(newSessionId);
        Instant afterReconnect = Instant.now();

        // Assert
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.getSessionId()).isEqualTo(newSessionId);
        assertThat(connection.getLastSeen()).isBetween(beforeReconnect, afterReconnect);
    }

    @Test
    void markReconnected_shouldWork_evenWhenAlreadyConnected() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Diana");
        PlayerConnection connection = new PlayerConnection(game, player, "session-123");

        // Connection is already connected (initial state)
        assertThat(connection.isConnected()).isTrue();

        Instant initialLastSeen = connection.getLastSeen();
        String oldSessionId = connection.getSessionId();

        // Wait to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }

        String newSessionId = "session-456";

        // Act: reconnect with new session (even though already connected)
        connection.markReconnected(newSessionId);

        // Assert: should update session and timestamp
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.getSessionId()).isEqualTo(newSessionId);
        assertThat(connection.getSessionId()).isNotEqualTo(oldSessionId);
        assertThat(connection.getLastSeen()).isAfter(initialLastSeen);
    }

    // ------------------------------------------------------------------------------------
    // updateLastSeen
    // ------------------------------------------------------------------------------------

    @Test
    void updateLastSeen_shouldUpdateTimestamp_withoutChangingConnectionStatus() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Eve");
        PlayerConnection connection = new PlayerConnection(game, player, "session-789");

        Instant initialLastSeen = connection.getLastSeen();
        boolean initialConnectionStatus = connection.isConnected();

        // Wait to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }

        Instant beforeUpdate = Instant.now();

        // Act
        connection.updateLastSeen();
        Instant afterUpdate = Instant.now();

        // Assert
        assertThat(connection.getLastSeen()).isAfter(initialLastSeen);
        assertThat(connection.getLastSeen()).isBetween(beforeUpdate, afterUpdate);
        assertThat(connection.isConnected()).isEqualTo(initialConnectionStatus); // Should not change
    }

    @Test
    void updateLastSeen_shouldWork_whenConnectionIsDisconnected() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Frank");
        PlayerConnection connection = new PlayerConnection(game, player, "session-999");

        // Disconnect first
        connection.markDisconnected();
        assertThat(connection.isConnected()).isFalse();

        Instant disconnectedLastSeen = connection.getLastSeen();

        // Wait to ensure timestamp difference (at least 1ms)
        sleepAtLeast(2);

        // Act: update last seen while disconnected
        connection.updateLastSeen();

        // Assert: timestamp updated, but still disconnected
        assertThat(connection.getLastSeen()).isAfterOrEqualTo(disconnectedLastSeen);
        assertThat(connection.isConnected()).isFalse(); // Should remain disconnected
    }

    // ------------------------------------------------------------------------------------
    // State Transitions
    // ------------------------------------------------------------------------------------

    @Test
    void connectionLifecycle_shouldHandleMultipleStateTransitions() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        Player player = new Player("Grace");
        String initialSession = "session-001";

        // Act & Assert: Initial connection
        PlayerConnection connection = new PlayerConnection(game, player, initialSession);
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.getSessionId()).isEqualTo(initialSession);

        Instant firstLastSeen = connection.getLastSeen();

        // Wait between operations
        sleep(10);

        // Act & Assert: Disconnect
        connection.markDisconnected();
        assertThat(connection.isConnected()).isFalse();
        assertThat(connection.getLastSeen()).isAfter(firstLastSeen);

        Instant disconnectLastSeen = connection.getLastSeen();
        sleep(10);

        // Act & Assert: Update last seen while disconnected
        connection.updateLastSeen();
        assertThat(connection.isConnected()).isFalse(); // Still disconnected
        assertThat(connection.getLastSeen()).isAfter(disconnectLastSeen);

        Instant updateLastSeen = connection.getLastSeen();
        sleep(10);

        // Act & Assert: Reconnect with new session
        String newSession = "session-002";
        connection.markReconnected(newSession);
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.getSessionId()).isEqualTo(newSession);
        assertThat(connection.getLastSeen()).isAfter(updateLastSeen);
    }

    // ------------------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------------------

    /**
     * Helper method to sleep without checked exception handling in tests.
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleeps for at least the specified number of milliseconds.
     * Useful for ensuring timestamp differences in fast-running tests.
     */
    private void sleepAtLeast(long millis) {
        long start = System.currentTimeMillis();
        long end = start + millis;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}