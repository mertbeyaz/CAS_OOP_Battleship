package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.Shot;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.controller.GameController;
import ch.battleship.battleshipbackend.web.api.dto.GamePublicDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(GameController.class)
class GameControllerShotsContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GameService gameService;

    @Test
    void fireShot_shouldReturnOnlyShotResultDto_contractTest() throws Exception {
        String gameCode = "TEST-CODE";
        UUID shooterId = UUID.randomUUID();

        Shot shot = mock(Shot.class);
        when(shot.getResult()).thenReturn(ShotResult.HIT);

        when(gameService.fireShot(eq(gameCode), eq(shooterId), eq(3), eq(3)))
                .thenReturn(shot);

        when(gameService.getPublicState(eq(gameCode), eq(shooterId)))
                .thenReturn(new GamePublicDto(gameCode, GameStatus.RUNNING, true, true, true, "Opponent"));

        MvcResult result = mockMvc.perform(post("/api/games/{gameCode}/shots", gameCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shooterId": "%s",
                                  "x": 3,
                                  "y": 3
                                }
                                """.formatted(shooterId)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // Whitelist (ShotResultDto)
        assertThat(json).contains("\"result\"");
        assertThat(json).contains("\"hit\"");
        assertThat(json).contains("\"shipSunk\"");
        assertThat(json).contains("\"yourTurn\"");

        // typische Leaks / falsches DTO
        assertThat(json).doesNotContain("gameCode");
        assertThat(json).doesNotContain("status");
        assertThat(json).doesNotContain("players");
        assertThat(json).doesNotContain("boards");
        assertThat(json).doesNotContain("placements");
        assertThat(json).doesNotContain("currentTurnPlayerId");
        assertThat(json).doesNotContain("winnerPlayerId");
        assertThat(json).doesNotContain("playerId");
        assertThat(json).doesNotContain("boardId");
    }

    @Test
    void fireShot_shouldReturnShotResultDto_only_contractTest() throws Exception {
        String gameCode = "TEST-CODE";
        UUID shooterId = UUID.randomUUID();

        Shot shot = mock(Shot.class);
        when(shot.getResult()).thenReturn(ShotResult.HIT);

        when(gameService.fireShot(eq(gameCode), eq(shooterId), eq(3), eq(3)))
                .thenReturn(shot);

        when(gameService.getPublicState(eq(gameCode), eq(shooterId)))
                .thenReturn(new GamePublicDto(gameCode, GameStatus.RUNNING, true, true, true, "Opponent"));

        mockMvc.perform(post("/api/games/{gameCode}/shots", gameCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shooterId": "%s",
                                  "x": 3,
                                  "y": 3
                                }
                                """.formatted(shooterId)))
                .andExpect(status().isOk())

                // exakt ShotResultDto
                .andExpect(jsonPath("$.result").value("HIT"))
                .andExpect(jsonPath("$.hit").value(true))
                .andExpect(jsonPath("$.shipSunk").value(false))
                .andExpect(jsonPath("$.yourTurn").value(true))

                // keine weiteren Top-Level-Felder (schema guard)
                .andExpect(jsonPath("$.*", hasSize(4)))

                // typische Leaks / falsches DTO
                .andExpect(jsonPath("$.gameCode").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.players").doesNotExist())
                .andExpect(jsonPath("$.boards").doesNotExist())
                .andExpect(jsonPath("$.placements").doesNotExist())
                .andExpect(jsonPath("$.currentTurnPlayerId").doesNotExist())
                .andExpect(jsonPath("$.winnerPlayerId").doesNotExist());
    }
}
