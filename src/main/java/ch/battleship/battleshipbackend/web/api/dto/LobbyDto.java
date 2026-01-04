package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;

import java.util.UUID;

/**
 * DTO returned by lobby matchmaking endpoints.
 *
 * <p>This DTO is optimized for frontend usage: after joining/creating a lobby, the client receives
 * all identifiers and initial state required to proceed without additional round-trips.
 *
 * <p>Includes:
 * <ul>
 *   <li>{@code lobbyCode} and {@code gameCode}</li>
 *   <li>lobby status</li>
 *   <li>the joining player's id and name</li>
 *   <li>the joining player's board state (including placements) for the setup phase</li>
 * </ul>
 *
 * <p>Security note:
 * The board state included here is the player's own board only. The opponent board is never included.
 *
 * @param lobbyCode public identifier of the lobby
 * @param gameCode public identifier of the game linked to the lobby
 * @param status lobby status as string (enum name)
 * @param myPlayerId id of the joining player
 * @param myPlayerName username of the joining player
 * @param myBoard state of the joining player's own board (may be null)
 */
public record LobbyDto(
        String lobbyCode,
        String gameCode,
        String status,
        UUID myPlayerId,
        String myPlayerName,
        BoardStateDto myBoard
) {

    /**
     * Creates a {@code LobbyDto} from the domain lobby plus the requesting player's context.
     *
     * <p>{@code myPlayer} and {@code myBoard} are optional to keep the mapper robust in edge cases.
     *
     * @param lobby domain lobby entity
     * @param myPlayer the player that joined/created the lobby (may be null)
     * @param myBoard the board belonging to {@code myPlayer} (may be null)
     * @return mapped DTO for frontend usage
     * @throws NullPointerException if {@code lobby} or {@code lobby.getGame()} is null
     */
    public static LobbyDto from(Lobby lobby, Player myPlayer, Board myBoard) {
        Game game = lobby.getGame();

        return new LobbyDto(
                lobby.getLobbyCode(),
                game.getGameCode(),
                lobby.getStatus().name(),
                myPlayer != null ? myPlayer.getId() : null,
                myPlayer != null ? myPlayer.getUsername() : null,
                myBoard != null ? BoardStateDto.from(myBoard) : null
        );
    }
}
