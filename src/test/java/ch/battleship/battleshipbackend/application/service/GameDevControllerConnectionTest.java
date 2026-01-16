package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.GameConfiguration;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.PlayerConnection;
import ch.battleship.battleshipbackend.repository.PlayerConnectionRepository;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.controller.GameDevController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ch.battleship.battleshipbackend.testutil.EntityTestUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link GameDevController} connection status endpoint.
 *
 * <p>Focus areas:
 * <ul>
 *   <li>Connection status retrieval for a game</li>
 *   <li>Response format and field validation</li>
 *   <li>Handling of missing games</li>
 *   <li>Empty connection lists</li>
 *   <li>Multiple player connections</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Uses {@link WebMvcTest} to test the controller layer in isolation</li>
 *   <li>Mock dependencies: {@link GameService} and {@link PlayerConnectionRepository}</li>
 *   <li>Tests require 'dev' or 'test' profile (controller is profile-restricted)</li>
 *   <li>Uses MockMvc for HTTP request simulation and JSON response validation</li>
 * </ul>
 */
@WebMvcTest(GameDevController.class)
@ActiveProfiles("test")
class GameDevControllerConnectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private PlayerConnectionRepository connectionRepository;

    // ------------------------------------------------------------------------------------
    // getConnectionStatus - Successful Retrieval
    // ------------------------------------------------------------------------------------

    @Test
    void getConnectionStatus_shouldReturnConnections_whenGameExists() throws Exception {
        // Arrange
        String gameCode = "ABC123";

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player1 = new Player("Alice");
        UUID player1Id = UUID.randomUUID();
        setId(player1, player1Id);

        Player player2 = new Player("Bob");
        UUID player2Id = UUID.randomUUID();
        setId(player2, player2Id);

        PlayerConnection conn1 = new PlayerConnection(game, player1, "session-1");
        PlayerConnection conn2 = new PlayerConnection(game, player2, "session-2");
        conn2.markDisconnected(); // Bob is disconnected

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGame(game)).thenReturn(List.of(conn1, conn2));

        // Act & Assert
        mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))

                // First connection (Alice - connected)
                .andExpect(jsonPath("$[0].playerName").value("Alice"))
                .andExpect(jsonPath("$[0].playerId").value(player1Id.toString()))
                .andExpect(jsonPath("$[0].connected").value(true))
                .andExpect(jsonPath("$[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$[0].lastSeen").exists())

                // Second connection (Bob - disconnected)
                .andExpect(jsonPath("$[1].playerName").value("Bob"))
                .andExpect(jsonPath("$[1].playerId").value(player2Id.toString()))
                .andExpect(jsonPath("$[1].connected").value(false))
                .andExpect(jsonPath("$[1].sessionId").value("session-2"))
                .andExpect(jsonPath("$[1].lastSeen").exists());
    }

    @Test
    void getConnectionStatus_shouldReturnEmptyArray_whenNoConnectionsExist() throws Exception {
        // Arrange
        String gameCode = "EMPTY123";

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGame(game)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getConnectionStatus_shouldIncludeAllFields_inResponse() throws Exception {
        // Arrange
        String gameCode = "FIELDS123";

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Charlie");
        UUID playerId = UUID.randomUUID();
        setId(player, playerId);

        PlayerConnection connection = new PlayerConnection(game, player, "session-xyz");

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGame(game)).thenReturn(List.of(connection));

        // Act
        MvcResult result = mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // Assert: verify all expected fields are present
        assertThat(json).contains("\"playerName\"");
        assertThat(json).contains("\"playerId\"");
        assertThat(json).contains("\"connected\"");
        assertThat(json).contains("\"lastSeen\"");
        assertThat(json).contains("\"sessionId\"");

        // Assert: verify field values
        assertThat(json).contains("\"playerName\":\"Charlie\"");
        assertThat(json).contains("\"playerId\":\"" + playerId.toString() + "\"");
        assertThat(json).contains("\"connected\":true");
        assertThat(json).contains("\"sessionId\":\"session-xyz\"");
    }

    // ------------------------------------------------------------------------------------
    // getConnectionStatus - Error Cases
    // ------------------------------------------------------------------------------------

    @Test
    void getConnectionStatus_shouldReturn404_whenGameNotFound() throws Exception {
        // Arrange
        String gameCode = "NOTFOUND";

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------
    // getConnectionStatus - Multiple Connections
    // ------------------------------------------------------------------------------------

    @Test
    void getConnectionStatus_shouldReturnMultipleConnections_inCorrectOrder() throws Exception {
        // Arrange
        String gameCode = "MULTI123";

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        // Create 3 players with different connection states
        Player player1 = new Player("Diana");
        UUID player1Id = UUID.randomUUID();
        setId(player1, player1Id);

        Player player2 = new Player("Eve");
        UUID player2Id = UUID.randomUUID();
        setId(player2, player2Id);

        Player player3 = new Player("Frank");
        UUID player3Id = UUID.randomUUID();
        setId(player3, player3Id);

        PlayerConnection conn1 = new PlayerConnection(game, player1, "session-d");
        PlayerConnection conn2 = new PlayerConnection(game, player2, "session-e");
        conn2.markDisconnected();
        PlayerConnection conn3 = new PlayerConnection(game, player3, "session-f");

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGame(game)).thenReturn(List.of(conn1, conn2, conn3));

        // Act & Assert
        mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))

                // Verify all three connections are present
                .andExpect(jsonPath("$[0].playerName").value("Diana"))
                .andExpect(jsonPath("$[0].connected").value(true))

                .andExpect(jsonPath("$[1].playerName").value("Eve"))
                .andExpect(jsonPath("$[1].connected").value(false))

                .andExpect(jsonPath("$[2].playerName").value("Frank"))
                .andExpect(jsonPath("$[2].connected").value(true));
    }

    // ------------------------------------------------------------------------------------
    // getConnectionStatus - Timestamp Validation
    // ------------------------------------------------------------------------------------

    @Test
    void getConnectionStatus_shouldIncludeValidTimestamp_inLastSeenField() throws Exception {
        // Arrange
        String gameCode = "TIME123";

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Grace");
        setId(player, UUID.randomUUID());

        PlayerConnection connection = new PlayerConnection(game, player, "session-time");

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGame(game)).thenReturn(List.of(connection));

        Instant before = Instant.now().minusSeconds(5);

        // Act
        MvcResult result = mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isOk())
                .andReturn();

        Instant after = Instant.now().plusSeconds(5);
        String json = result.getResponse().getContentAsString();

        // Assert: lastSeen should be a valid ISO-8601 timestamp
        assertThat(json).containsPattern("\"lastSeen\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

        // Extract and validate timestamp is within reasonable range
        String lastSeenStr = json.split("\"lastSeen\":\"")[1].split("\"")[0];
        Instant lastSeen = Instant.parse(lastSeenStr);

        assertThat(lastSeen).isBetween(before, after);
    }

    // ------------------------------------------------------------------------------------
    // getConnectionStatus - Null Session ID
    // ------------------------------------------------------------------------------------

    @Test
    void getConnectionStatus_shouldHandleNullSessionId() throws Exception {
        // Arrange
        String gameCode = "NULL123";

        Game game = new Game(gameCode, GameConfiguration.defaultConfig());
        setId(game, UUID.randomUUID());

        Player player = new Player("Henry");
        setId(player, UUID.randomUUID());

        // Create connection with null session ID (edge case)
        PlayerConnection connection = new PlayerConnection(game, player, null);

        when(gameService.getByGameCode(gameCode)).thenReturn(Optional.of(game));
        when(connectionRepository.findByGame(game)).thenReturn(List.of(connection));

        // Act & Assert
        mockMvc.perform(get("/api/dev/games/{gameCode}/connections", gameCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value((String) null));
    }
}