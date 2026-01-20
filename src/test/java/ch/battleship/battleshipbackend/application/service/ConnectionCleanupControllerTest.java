package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import ch.battleship.battleshipbackend.service.ConnectionCleanupService;
import ch.battleship.battleshipbackend.web.api.controller.ConnectionCleanupController;
import ch.battleship.battleshipbackend.web.api.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConnectionCleanupController}.
 *
 * <p>Focus areas:
 * <ul>
 *   <li>Manual cleanup triggering via REST endpoint</li>
 *   <li>Status inspection and reporting</li>
 *   <li>Configuration display</li>
 *   <li>Connection listing and details</li>
 *   <li>Nuclear delete-all operation</li>
 *   <li>Proper DTO mapping from entities</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Uses Mockito to simulate service and repository interactions</li>
 *   <li>Tests are profile-agnostic (profile check is Spring's responsibility)</li>
 *   <li>Focuses on business logic and DTO mapping, not HTTP layer</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ConnectionCleanupControllerTest {

    @Mock
    private ConnectionCleanupService cleanupService;

    @Mock
    private PlayerConnectionRepository connectionRepository;

    @InjectMocks
    private ConnectionCleanupController controller;

    // ------------------------------------------------------------------------------------
    // POST /cleanup/connections - Trigger Cleanup
    // ------------------------------------------------------------------------------------

    @Test
    void triggerConnectionCleanup_shouldReturnResult_whenConnectionsDeleted() {
        // Arrange
        when(connectionRepository.count())
                .thenReturn(10L)  // Before cleanup
                .thenReturn(5L);  // After cleanup

        when(cleanupService.getCleanupThresholdHours()).thenReturn(24);

        // Act
        ResponseEntity<CleanupResultDto> response = controller.triggerConnectionCleanup();

        // Assert
        verify(cleanupService, times(1)).triggerCleanup();
        verify(connectionRepository, times(2)).count();

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        CleanupResultDto result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.message()).isEqualTo("Cleanup completed");
        assertThat(result.deletedCount()).isEqualTo(5);
        assertThat(result.connectionsBefore()).isEqualTo(10);
        assertThat(result.connectionsAfter()).isEqualTo(5);
        assertThat(result.thresholdHours()).isEqualTo(24);
    }

    @Test
    void triggerConnectionCleanup_shouldReturnZeroDeleted_whenNoOldConnections() {
        // Arrange
        when(connectionRepository.count())
                .thenReturn(5L)  // Before cleanup
                .thenReturn(5L); // After cleanup (nothing deleted)

        when(cleanupService.getCleanupThresholdHours()).thenReturn(24);

        // Act
        ResponseEntity<CleanupResultDto> response = controller.triggerConnectionCleanup();

        // Assert
        CleanupResultDto result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.deletedCount()).isEqualTo(0);
        assertThat(result.connectionsBefore()).isEqualTo(5);
        assertThat(result.connectionsAfter()).isEqualTo(5);
    }

    @Test
    void triggerConnectionCleanup_shouldHandleEmptyDatabase() {
        // Arrange
        when(connectionRepository.count())
                .thenReturn(0L)  // Before cleanup
                .thenReturn(0L); // After cleanup

        when(cleanupService.getCleanupThresholdHours()).thenReturn(24);

        // Act
        ResponseEntity<CleanupResultDto> response = controller.triggerConnectionCleanup();

        // Assert
        CleanupResultDto result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.deletedCount()).isEqualTo(0);
        assertThat(result.connectionsBefore()).isEqualTo(0);
        assertThat(result.connectionsAfter()).isEqualTo(0);
    }

    // ------------------------------------------------------------------------------------
    // GET /cleanup/status - Get Cleanup Status
    // ------------------------------------------------------------------------------------

    @Test
    void getCleanupStatus_shouldReturnStatistics_whenConnectionsExist() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player1 = new Player("Alice");
        setId(player1, UUID.randomUUID());

        Player player2 = new Player("Bob");
        setId(player2, UUID.randomUUID());

        // Old connection (30 hours ago)
        PlayerConnection oldConnection = new PlayerConnection(game, player1, "session-old");
        setLastSeen(oldConnection, Instant.now().minusSeconds(30 * 3600));

        // Recent connection (1 hour ago)
        PlayerConnection recentConnection = new PlayerConnection(game, player2, "session-recent");
        setLastSeen(recentConnection, Instant.now().minusSeconds(3600));

        when(connectionRepository.findAll()).thenReturn(List.of(oldConnection, recentConnection));
        when(cleanupService.getCleanupThresholdHours()).thenReturn(24);

        // Act
        ResponseEntity<CleanupStatusDto> response = controller.getCleanupStatus();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        CleanupStatusDto status = response.getBody();
        assertThat(status).isNotNull();
        assertThat(status.totalConnections()).isEqualTo(2);
        assertThat(status.oldConnections()).isEqualTo(1);
        assertThat(status.recentConnections()).isEqualTo(1);
        assertThat(status.thresholdHours()).isEqualTo(24);
        assertThat(status.thresholdTimestamp()).isNotNull();
    }

    @Test
    void getCleanupStatus_shouldReturnZeros_whenNoConnections() {
        // Arrange
        when(connectionRepository.findAll()).thenReturn(List.of());
        when(cleanupService.getCleanupThresholdHours()).thenReturn(24);

        // Act
        ResponseEntity<CleanupStatusDto> response = controller.getCleanupStatus();

        // Assert
        CleanupStatusDto status = response.getBody();
        assertThat(status).isNotNull();
        assertThat(status.totalConnections()).isEqualTo(0);
        assertThat(status.oldConnections()).isEqualTo(0);
        assertThat(status.recentConnections()).isEqualTo(0);
    }

    @Test
    void getCleanupStatus_shouldRespectCustomThreshold_when48Hours() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Charlie");
        setId(player, UUID.randomUUID());

        // Connection 30 hours old - old with 24h threshold, recent with 48h threshold
        PlayerConnection connection30h = new PlayerConnection(game, player, "session-30h");
        setLastSeen(connection30h, Instant.now().minusSeconds(30 * 3600));

        when(connectionRepository.findAll()).thenReturn(List.of(connection30h));
        when(cleanupService.getCleanupThresholdHours()).thenReturn(48); // 48h threshold

        // Act
        ResponseEntity<CleanupStatusDto> response = controller.getCleanupStatus();

        // Assert
        CleanupStatusDto status = response.getBody();
        assertThat(status).isNotNull();
        assertThat(status.totalConnections()).isEqualTo(1);
        assertThat(status.oldConnections()).isEqualTo(0);  // Not old with 48h threshold
        assertThat(status.recentConnections()).isEqualTo(1);
        assertThat(status.thresholdHours()).isEqualTo(48);
    }

    // ------------------------------------------------------------------------------------
    // GET /cleanup/config - Get Configuration
    // ------------------------------------------------------------------------------------

    @Test
    void getCleanupConfig_shouldReturnConfiguration() {
        // Arrange
        when(cleanupService.getCleanupIntervalMs()).thenReturn(3600000L); // 1 hour
        when(cleanupService.getCleanupThresholdHours()).thenReturn(24);

        // Act
        ResponseEntity<CleanupConfigDto> response = controller.getCleanupConfig();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        CleanupConfigDto config = response.getBody();
        assertThat(config).isNotNull();
        assertThat(config.cleanupIntervalMs()).isEqualTo(3600000L);
        assertThat(config.cleanupIntervalHours()).isEqualTo(1.0);
        assertThat(config.cleanupThresholdHours()).isEqualTo(24);
    }

    @Test
    void getCleanupConfig_shouldCalculateHoursCorrectly_forCustomInterval() {
        // Arrange
        when(cleanupService.getCleanupIntervalMs()).thenReturn(7200000L); // 2 hours
        when(cleanupService.getCleanupThresholdHours()).thenReturn(48);

        // Act
        ResponseEntity<CleanupConfigDto> response = controller.getCleanupConfig();

        // Assert
        CleanupConfigDto config = response.getBody();
        assertThat(config).isNotNull();
        assertThat(config.cleanupIntervalMs()).isEqualTo(7200000L);
        assertThat(config.cleanupIntervalHours()).isEqualTo(2.0);
        assertThat(config.cleanupThresholdHours()).isEqualTo(48);
    }

    // ------------------------------------------------------------------------------------
    // DELETE /cleanup/all - Delete All Connections
    // ------------------------------------------------------------------------------------

    @Test
    void deleteAllConnections_shouldReturnDeletedCount() {
        // Arrange
        when(connectionRepository.count()).thenReturn(15L);

        // Act
        ResponseEntity<DeleteAllResultDto> response = controller.deleteAllConnections();

        // Assert
        verify(connectionRepository, times(1)).deleteAll();

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        DeleteAllResultDto result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.message()).isEqualTo("All connections deleted");
        assertThat(result.deletedCount()).isEqualTo(15);
    }

    @Test
    void deleteAllConnections_shouldHandleEmptyDatabase() {
        // Arrange
        when(connectionRepository.count()).thenReturn(0L);

        // Act
        ResponseEntity<DeleteAllResultDto> response = controller.deleteAllConnections();

        // Assert
        verify(connectionRepository, times(1)).deleteAll();

        DeleteAllResultDto result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.deletedCount()).isEqualTo(0);
    }

    // ------------------------------------------------------------------------------------
    // GET /cleanup/connections - Get All Connections
    // ------------------------------------------------------------------------------------

    @Test
    void getAllConnections_shouldReturnListOfConnectionInfoDtos() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        game.setGameCode("ABC123");
        setId(game, UUID.randomUUID());

        Player player1 = new Player("Alice");
        setId(player1, UUID.randomUUID());

        Player player2 = new Player("Bob");
        setId(player2, UUID.randomUUID());

        PlayerConnection conn1 = new PlayerConnection(game, player1, "session-1");
        conn1.markReconnected("session-1"); // connected = true
        setLastSeen(conn1, Instant.now().minusSeconds(1800)); // 30 minutes ago

        PlayerConnection conn2 = new PlayerConnection(game, player2, "session-2");
        conn2.markDisconnected(); // connected = false
        setLastSeen(conn2, Instant.now().minusSeconds(7200)); // 2 hours ago

        when(connectionRepository.findAll()).thenReturn(List.of(conn1, conn2));

        // Act
        ResponseEntity<List<ConnectionInfoDto>> response = controller.getAllConnections();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        List<ConnectionInfoDto> connections = response.getBody();
        assertThat(connections).isNotNull();
        assertThat(connections).hasSize(2);

        // Check first connection
        ConnectionInfoDto info1 = connections.get(0);
        assertThat(info1.playerName()).isEqualTo("Alice");
        assertThat(info1.gameCode()).isEqualTo("ABC123");
        assertThat(info1.sessionId()).isEqualTo("session-1");
        assertThat(info1.connected()).isTrue();
        assertThat(info1.ageHours()).isGreaterThan(0.0).isLessThan(1.0);

        // Check second connection
        ConnectionInfoDto info2 = connections.get(1);
        assertThat(info2.playerName()).isEqualTo("Bob");
        assertThat(info2.gameCode()).isEqualTo("ABC123");
        assertThat(info2.sessionId()).isEqualTo("session-2");
        assertThat(info2.connected()).isFalse();
        assertThat(info2.ageHours()).isGreaterThan(1.0).isLessThan(3.0);
    }

    @Test
    void getAllConnections_shouldReturnEmptyList_whenNoConnections() {
        // Arrange
        when(connectionRepository.findAll()).thenReturn(List.of());

        // Act
        ResponseEntity<List<ConnectionInfoDto>> response = controller.getAllConnections();

        // Assert
        List<ConnectionInfoDto> connections = response.getBody();
        assertThat(connections).isNotNull();
        assertThat(connections).isEmpty();
    }

    @Test
    void getAllConnections_shouldCalculateAgeCorrectly() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        game.setGameCode("XYZ789");
        setId(game, UUID.randomUUID());

        Player player = new Player("Charlie");
        setId(player, UUID.randomUUID());

        PlayerConnection conn = new PlayerConnection(game, player, "session-test");
        setLastSeen(conn, Instant.now().minusSeconds(5 * 3600)); // Exactly 5 hours ago

        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        // Act
        ResponseEntity<List<ConnectionInfoDto>> response = controller.getAllConnections();

        // Assert
        List<ConnectionInfoDto> connections = response.getBody();
        assertThat(connections).hasSize(1);

        ConnectionInfoDto info = connections.get(0);
        // Age should be approximately 5.0 hours (allowing small margin for test execution time)
        assertThat(info.ageHours()).isGreaterThan(4.99).isLessThan(5.01);
    }

    @Test
    void getAllConnections_shouldRoundAgeToTwoDecimals() {
        // Arrange
        Game game = new Game(GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Dave");
        setId(player, UUID.randomUUID());

        PlayerConnection conn = new PlayerConnection(game, player, "session-round");
        // Set to 1.234567 hours ago
        setLastSeen(conn, Instant.now().minusSeconds((long)(1.234567 * 3600)));

        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        // Act
        ResponseEntity<List<ConnectionInfoDto>> response = controller.getAllConnections();

        // Assert
        List<ConnectionInfoDto> connections = response.getBody();
        ConnectionInfoDto info = connections.get(0);

        // Should be rounded to 2 decimals: 1.23
        assertThat(info.ageHours()).isGreaterThan(1.22).isLessThan(1.24);
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