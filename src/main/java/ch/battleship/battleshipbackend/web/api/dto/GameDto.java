package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Game;

import java.util.UUID;
/*
@Data
public class GameDto {
    private UUID id;
    private String gameCode;
    private GameStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static GameDto from(Game game) {
        GameDto dto = new GameDto();
        dto.setId(game.getId());
        dto.setGameCode(game.getGameCode());
        dto.setStatus(game.getStatus());
        dto.setCreatedAt(game.getCreatedAt());
        dto.setUpdatedAt(game.getUpdatedAt());
        return dto;
    }
}
*/

import java.util.List;

public record GameDto(
        UUID id,
        String gameCode,
        String status,
        int boardWidth,
        int boardHeight,
        UUID currentTurnPlayerId,
        UUID winnerPlayerId,
        List<PlayerDto> players,
        List<BoardDto> boards
) {
    public static GameDto from(Game game) {
        var config = game.getConfig();
        return new GameDto(
                game.getId(),
                game.getGameCode(),
                game.getStatus().name(),
                config.getBoardWidth(),
                config.getBoardHeight(),
                game.getCurrentTurnPlayerId(),
                game.getWinnerPlayerId(),
                game.getPlayers().stream().map(PlayerDto::from).toList(),
                game.getBoards().stream().map(BoardDto::from).toList()
        );
    }
}
