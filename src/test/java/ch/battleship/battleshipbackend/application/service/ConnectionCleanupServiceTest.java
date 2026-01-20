package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import ch.battleship.battleshipbackend.service.ConnectionCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConnectionCleanupService}.
 *
 * <p>Focus areas:
 * <ul>
 *   <li>Automatic cleanup of old connections based on threshold</li>
 *   <li>Preservation of recent connections</li>
 *   <li>Manual cleanup triggering</li>
 *   <li>Correct identification of stale connections</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Tests use Mockito to simulate repository interactions</li>
 *   <li>Time-based tests use Instant calculations for threshold verification</li>
 *   <li>Scheduled execution is not tested (Spring scheduling framework responsibility)</li>
 *   <li>Uses ReflectionTestUtils to set @Value properties for testing</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ConnectionCleanupServiceTest {

    @Mock
    private PlayerConnectionRepository connectionRepository;

    @InjectMocks
    private ConnectionCleanupService cleanupService;

    /**
     * Sets up default property values for each test.
     * These simulate the @Value properties from application.properties.
     */
    @BeforeEach
    void setUp() {
        // Set default values for @Value properties
        ReflectionTestUtils.setField(cleanupService, "cleanupIntervalMs", 3600000L); // 1 hour
        ReflectionTestUtils.setField(cleanupService, "cleanupThresholdHours", 24);   // 24 hours
    }

    // ------------------------------------------------------------------------------------
    // cleanupOldConnections - Basic Cleanup
    // ------------------------------------------------------------------------------------

    @Test
    void cleanupOldConnections_shouldDeleteOldConnections_whenThresholdExceeded() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player1 = new Player("Alice");
        setId(player1, UUID.randomUUID());

        Player player2 = new Player("Bob");
        setId(player2, UUID.randomUUID());

        // Create an old connection (25 hours ago - older than 24h threshold)
        PlayerConnection oldConnection = new PlayerConnection(game, player1, "session-old");
        setLastSeen(oldConnection, Instant.now().minusSeconds(25 * 3600));

        // Create a recent connection (1 hour ago - within threshold)
        PlayerConnection recentConnection = new PlayerConnection(game, player2, "session-recent");
        setLastSeen(recentConnection, Instant.now().minusSeconds(3600));

        List<PlayerConnection> allConnections = List.of(oldConnection, recentConnection);

        when(connectionRepository.findAll()).thenReturn(allConnections);

        ArgumentCaptor<List<PlayerConnection>> deleteCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: only old connection should be deleted
        verify(connectionRepository, times(1)).deleteAll(deleteCaptor.capture());

        List<PlayerConnection> deletedConnections = deleteCaptor.getValue();
        assertThat(deletedConnections).hasSize(1);
        assertThat(deletedConnections.get(0)).isEqualTo(oldConnection);
    }

    @Test
    void cleanupOldConnections_shouldNotDeleteAnything_whenAllConnectionsAreRecent() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Charlie");
        setId(player, UUID.randomUUID());

        // Create recent connections (all within 24h threshold)
        PlayerConnection conn1 = new PlayerConnection(game, player, "session-1");
        setLastSeen(conn1, Instant.now().minusSeconds(3600)); // 1 hour ago

        PlayerConnection conn2 = new PlayerConnection(game, player, "session-2");
        setLastSeen(conn2, Instant.now().minusSeconds(7200)); // 2 hours ago

        PlayerConnection conn3 = new PlayerConnection(game, player, "session-3");
        setLastSeen(conn3, Instant.now().minusSeconds(10 * 3600)); // 10 hours ago

        List<PlayerConnection> allConnections = List.of(conn1, conn2, conn3);

        when(connectionRepository.findAll()).thenReturn(allConnections);

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: no deletions should occur
        verify(connectionRepository, never()).deleteAll(anyList());
    }

    @Test
    void cleanupOldConnections_shouldHandleEmptyConnectionList() {
        // Arrange
        when(connectionRepository.findAll()).thenReturn(List.of());

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: no deletions should be attempted
        verify(connectionRepository, never()).deleteAll(anyList());
    }

    @Test
    void cleanupOldConnections_shouldDeleteAllConnections_whenAllAreOld() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Diana");
        setId(player, UUID.randomUUID());

        // Create multiple old connections (all older than 24h)
        List<PlayerConnection> oldConnections = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            PlayerConnection conn = new PlayerConnection(game, player, "session-" + i);
            setLastSeen(conn, Instant.now().minusSeconds((25 + i) * 3600)); // 25-29 hours ago
            oldConnections.add(conn);
        }

        when(connectionRepository.findAll()).thenReturn(oldConnections);

        ArgumentCaptor<List<PlayerConnection>> deleteCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: all connections should be deleted
        verify(connectionRepository, times(1)).deleteAll(deleteCaptor.capture());

        List<PlayerConnection> deletedConnections = deleteCaptor.getValue();
        assertThat(deletedConnections).hasSize(5);
        assertThat(deletedConnections).containsExactlyInAnyOrderElementsOf(oldConnections);
    }

    // ------------------------------------------------------------------------------------
    // cleanupOldConnections - Edge Cases
    // ------------------------------------------------------------------------------------

    @Test
    void cleanupOldConnections_shouldDeleteConnection_whenExactlyAtThreshold() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Eve");
        setId(player, UUID.randomUUID());

        // Create connection exactly at 24h threshold
        PlayerConnection thresholdConnection = new PlayerConnection(game, player, "session-threshold");
        setLastSeen(thresholdConnection, Instant.now().minusSeconds(24 * 3600)); // Exactly 24 hours

        when(connectionRepository.findAll()).thenReturn(List.of(thresholdConnection));

        ArgumentCaptor<List<PlayerConnection>> deleteCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: connection at threshold should be deleted (isBefore, not isBeforeOrEqual)
        verify(connectionRepository, times(1)).deleteAll(deleteCaptor.capture());

        List<PlayerConnection> deletedConnections = deleteCaptor.getValue();
        assertThat(deletedConnections).hasSize(1);
    }

    @Test
    void cleanupOldConnections_shouldPreserveConnection_whenJustWithinThreshold() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Frank");
        setId(player, UUID.randomUUID());

        // Create connection just within threshold (23.99 hours)
        PlayerConnection recentConnection = new PlayerConnection(game, player, "session-recent");
        setLastSeen(recentConnection, Instant.now().minusSeconds(24 * 3600 - 60)); // 23h 59min ago

        when(connectionRepository.findAll()).thenReturn(List.of(recentConnection));

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: no deletions should occur
        verify(connectionRepository, never()).deleteAll(anyList());
    }

    // ------------------------------------------------------------------------------------
    // cleanupOldConnections - Custom Thresholds
    // ------------------------------------------------------------------------------------

    @Test
    void cleanupOldConnections_shouldRespectCustomThreshold_when48Hours() {
        // Arrange: Set custom threshold of 48 hours
        ReflectionTestUtils.setField(cleanupService, "cleanupThresholdHours", 48);

        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Custom");
        setId(player, UUID.randomUUID());

        // Connection 30 hours old - would be deleted with 24h threshold, but not with 48h
        PlayerConnection connection30h = new PlayerConnection(game, player, "session-30h");
        setLastSeen(connection30h, Instant.now().minusSeconds(30 * 3600));

        // Connection 50 hours old - should be deleted even with 48h threshold
        PlayerConnection connection50h = new PlayerConnection(game, player, "session-50h");
        setLastSeen(connection50h, Instant.now().minusSeconds(50 * 3600));

        when(connectionRepository.findAll()).thenReturn(List.of(connection30h, connection50h));

        ArgumentCaptor<List<PlayerConnection>> deleteCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        cleanupService.cleanupOldConnections();

        // Assert: only 50h connection should be deleted
        verify(connectionRepository, times(1)).deleteAll(deleteCaptor.capture());

        List<PlayerConnection> deletedConnections = deleteCaptor.getValue();
        assertThat(deletedConnections).hasSize(1);
        assertThat(deletedConnections.get(0)).isEqualTo(connection50h);
    }

    // ------------------------------------------------------------------------------------
    // triggerCleanup - Manual Trigger
    // ------------------------------------------------------------------------------------

    @Test
    void triggerCleanup_shouldPerformCleanup_whenCalledManually() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Grace");
        setId(player, UUID.randomUUID());

        PlayerConnection oldConnection = new PlayerConnection(game, player, "session-manual");
        setLastSeen(oldConnection, Instant.now().minusSeconds(30 * 3600)); // 30 hours ago

        when(connectionRepository.findAll()).thenReturn(List.of(oldConnection));

        ArgumentCaptor<List<PlayerConnection>> deleteCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        cleanupService.triggerCleanup();

        // Assert: cleanup should be executed
        verify(connectionRepository, times(1)).deleteAll(deleteCaptor.capture());

        List<PlayerConnection> deletedConnections = deleteCaptor.getValue();
        assertThat(deletedConnections).hasSize(1);
        assertThat(deletedConnections.get(0)).isEqualTo(oldConnection);
    }

    // ------------------------------------------------------------------------------------
    // Getters (now testing @Value properties)
    // ------------------------------------------------------------------------------------

    @Test
    void getCleanupThresholdHours_shouldReturnConfiguredValue() {
        // Act
        int threshold = cleanupService.getCleanupThresholdHours();

        // Assert: should return value set via ReflectionTestUtils in @BeforeEach
        assertThat(threshold).isEqualTo(24);
    }

    @Test
    void getCleanupIntervalMs_shouldReturnConfiguredValue() {
        // Act
        long interval = cleanupService.getCleanupIntervalMs();

        // Assert: should return value set via ReflectionTestUtils in @BeforeEach
        assertThat(interval).isEqualTo(3600000); // 1 hour in milliseconds
    }

    @Test
    void getCleanupThresholdHours_shouldReturnCustomValue_whenChanged() {
        // Arrange: Set custom value
        ReflectionTestUtils.setField(cleanupService, "cleanupThresholdHours", 48);

        // Act
        int threshold = cleanupService.getCleanupThresholdHours();

        // Assert
        assertThat(threshold).isEqualTo(48);
    }

    // ------------------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------------------

    /**
     * Helper method to set the lastSeen timestamp on a PlayerConnection.
     * Uses reflection since lastSeen is updated via business methods.
     */
    private void setLastSeen(PlayerConnection connection, Instant timestamp) {
        try {
            var field = PlayerConnection.class.getDeclaredField("lastSeen");
            field.setAccessible(true);
            field.set(connection, timestamp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set lastSeen via reflection", e);
        }
    }
}