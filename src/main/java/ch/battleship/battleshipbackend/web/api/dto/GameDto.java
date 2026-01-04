package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Game;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing a game including basic configuration and related entities.
 *
 * <p>This DTO is a convenient aggregated view that includes:
 * <ul>
 *   <li>game metadata (id, code, status)</li>
 *   <li>board dimensions from the configuration</li>
 *   <li>turn and winner information</li>
 *   <li>players and boards as nested DTOs</li>
 * </ul>
 *
 * <p>Important:
 * This DTO may expose more internal details (e.g. ids) than a minimal public game state.
 * Therefore, it should be used only where appropriate (e.g. internal/admin/dev endpoints),
 * and not where it could enable cheating or leak unnecessary data.
 *
 * @param id internal database id of the game
 * @param gameCode public identifier of the game
 * @param status current status as string (enum name)
 * @param boardWidth configured board width
 * @param boardHeight configured board height
 * @param currentTurnPlayerId player id whose turn it currently is (may be null)
 * @param winnerPlayerId winner player id if finished (may be null)
 * @param players players of the game
 * @param boards boards of the game
 */
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

    /**
     * Creates a {@code GameDto} from the given domain {@link Game}.
     *
     * @param game domain game entity
     * @return mapped DTO including players and boards
     * @throws NullPointerException if {@code game} or {@code game.getConfig()} is null
     */
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
