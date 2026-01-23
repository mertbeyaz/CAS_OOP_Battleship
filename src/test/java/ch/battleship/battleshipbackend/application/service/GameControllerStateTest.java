package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.controller.GameController;
import ch.battleship.battleshipbackend.web.api.dto.GameSnapshotDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for the game snapshot endpoint: {@code GET /api/games/{gameCode}/state}.
 *
 * <p>Focus:
 * <ul>
 *   <li>HTTP 200 with snapshot JSON for valid requests</li>
 *   <li>HTTP status mapping for domain/service exceptions (404 / 400)</li>
 *   <li>Request parameter validation (playerId must be a UUID)</li>
 * </ul>
 */
@WebMvcTest(GameController.class)
class GameControllerStateTest {

    private static final String GAME_CODE = "TEST-GAME";
    private static final UUID PLAYER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GameService gameService;

    @Test
    void getGameState_shouldReturn200_andSnapshotJson() throws Exception {
        // Arrange
        GameSnapshotDto snapshot = new GameSnapshotDto(
                GAME_CODE,
                GameStatus.RUNNING,
                10,
                10,
                PLAYER_ID.toString(),
                "PlayerA",
                "PlayerB",
                true,
                true,
                true,
                null,          // yourBoard (optional)
                List.of(),     // shotsOnYourBoard
                List.of()      // yourShotsOnOpponent
        );

        when(gameService.getSnapshot(GAME_CODE, PLAYER_ID)).thenReturn(snapshot);

        // Act + Assert
        mockMvc.perform(get("/api/games/{gameCode}/state", GAME_CODE)
                        .queryParam("playerId", PLAYER_ID.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.gameCode").value(GAME_CODE))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        verify(gameService).getSnapshot(GAME_CODE, PLAYER_ID);
        verifyNoMoreInteractions(gameService);
    }

    @Test
    void getGameState_shouldReturn404_whenGameMissing() throws Exception {
        // Arrange
        when(gameService.getSnapshot(GAME_CODE, PLAYER_ID))
                .thenThrow(new EntityNotFoundException("Game not found"));

        // Act + Assert
        mockMvc.perform(get("/api/games/{gameCode}/state", GAME_CODE)
                        .queryParam("playerId", PLAYER_ID.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(gameService).getSnapshot(GAME_CODE, PLAYER_ID);
        verifyNoMoreInteractions(gameService);
    }

    @Test
    void getGameState_shouldReturn400_whenIllegalState() throws Exception {
        // Arrange
        when(gameService.getSnapshot(GAME_CODE, PLAYER_ID))
                .thenThrow(new IllegalStateException("Player not in game"));

        // Act + Assert
        mockMvc.perform(get("/api/games/{gameCode}/state", GAME_CODE)
                        .queryParam("playerId", PLAYER_ID.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(gameService).getSnapshot(GAME_CODE, PLAYER_ID);
        verifyNoMoreInteractions(gameService);
    }

    @Test
    void getGameState_shouldReturn400_whenPlayerIdIsNotUuid() throws Exception {
        // Act + Assert
        mockMvc.perform(get("/api/games/{gameCode}/state", GAME_CODE)
                        .queryParam("playerId", "not-a-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }
}
