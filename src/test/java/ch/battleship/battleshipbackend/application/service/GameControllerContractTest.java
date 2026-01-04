package ch.battleship.battleshipbackend.application.service;

import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.service.GameService;
import ch.battleship.battleshipbackend.web.api.controller.GameController;
import ch.battleship.battleshipbackend.web.api.dto.GamePublicDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for {@link GameController}.
 *
 * <p>Focus:
 * Ensure that the public game endpoint returns only the allowed fields and does not leak
 * internal identifiers or gameplay-relevant information (anti-cheat / encapsulation).
 *
 * <p>This test intentionally inspects the raw JSON response to verify the API surface,
 * independent of Java DTO mapping.
 */
@WebMvcTest(GameController.class)
class GameControllerContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GameService gameService;

    @Test
    void getGame_shouldReturnOnlyPublicFields_contractTest() throws Exception {
        // Arrange
        UUID playerId = UUID.randomUUID();

        GamePublicDto dto = new GamePublicDto(
                "TEST-CODE",
                GameStatus.SETUP,
                false,
                false,
                false,
                null
        );

        when(gameService.getPublicState("TEST-CODE", playerId)).thenReturn(dto);

        // Act
        MvcResult result = mockMvc.perform(get("/api/games/TEST-CODE")
                        .param("playerId", playerId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // Assert (whitelist): these public fields must be present
        assertThat(json).contains("\"gameCode\"");
        assertThat(json).contains("\"status\"");
        assertThat(json).contains("\"yourBoardLocked\"");
        assertThat(json).contains("\"opponentBoardLocked\"");
        assertThat(json).contains("\"yourTurn\"");
        assertThat(json).contains("\"opponentName\"");

        // Assert (blacklist): internal fields must not be present (prevent leaks / cheating)
        assertThat(json).doesNotContain("playerId");
        assertThat(json).doesNotContain("boardId");
        assertThat(json).doesNotContain("placements");
        assertThat(json).doesNotContain("players");
        assertThat(json).doesNotContain("boards");
        assertThat(json).doesNotContain("currentTurnPlayerId");
        assertThat(json).doesNotContain("winnerPlayerId");
        assertThat(json).doesNotContain("shots");
    }
}
