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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(GameController.class)
class GameControllerContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GameService gameService;

    @Test
    void getGame_shouldReturnOnlyPublicFields_contractTest() throws Exception {
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

        MvcResult result = mockMvc.perform(get("/api/games/TEST-CODE")
                        .param("playerId", playerId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // Whitelist: Diese Felder müssen drin sein
        assertThat(json).contains("\"gameCode\"");
        assertThat(json).contains("\"status\"");
        assertThat(json).contains("\"yourBoardLocked\"");
        assertThat(json).contains("\"opponentBoardLocked\"");
        assertThat(json).contains("\"yourTurn\"");
        assertThat(json).contains("\"opponentName\"");

        // Leaks / interne Felder dürfen NICHT drin sein
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
