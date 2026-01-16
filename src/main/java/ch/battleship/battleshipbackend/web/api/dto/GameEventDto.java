package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket event DTO used to notify clients about game state changes.
 *
 * <p>This DTO is published to a game-specific topic (e.g. {@code /topic/games/{gameCode}/events})
 * and contains:
 * <ul>
 *   <li>an event {@link GameEventType} (what happened)</li>
 *   <li>the affected {@code gameCode}</li>
 *   <li>the current {@link GameStatus} at the time of the event</li>
 *   <li>a timestamp</li>
 *   <li>a flexible payload map with event-specific fields</li>
 * </ul>
 *
 * <p>Design note:
 * The payload is modeled as {@code Map<String, Object>} to keep the WebSocket protocol lightweight
 * and to allow different event types to carry different data. The tradeoff is less compile-time
 * type safety compared to dedicated payload DTOs per event type.
 *
 * @param type event type
 * @param gameCode affected game identifier
 * @param gameStatus game status at event emission time
 * @param timeStamp event timestamp
 * @param payload event-specific data
 */
public record GameEventDto(
        GameEventType type,
        String gameCode,
        GameStatus gameStatus,
        Instant timeStamp,
        Map<String, Object> payload
) {

    /**
     * Creates an event indicating that a player confirmed (locked) their board.
     *
     * @param game game instance
     * @param player confirming player
     * @return event DTO
     */
    public static GameEventDto boardConfirmed(Game game, Player player) {
        return new GameEventDto(
                GameEventType.BOARD_CONFIRMED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "playerName", player.getUsername()
                )
        );
    }

    /**
     * Creates an event indicating that a player re-rolled their board (auto placement).
     *
     * @param game game instance
     * @param player player who re-rolled the board
     * @return event DTO
     */
    public static GameEventDto boardRerolled(Game game, Player player) {
        return new GameEventDto(
                GameEventType.BOARD_REROLLED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "playerName", player.getUsername()
                )
        );
    }

    /**
     * Creates an event indicating that the game started and whose turn it is first.
     *
     * @param game game instance
     * @param currentTurnPlayer player who starts
     * @return event DTO
     */
    public static GameEventDto gameStarted(Game game, Player currentTurnPlayer) {
        return new GameEventDto(
                GameEventType.GAME_STARTED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "currentTurnPlayerName", currentTurnPlayer.getUsername()
                )
        );
    }

    /**
     * Creates an event indicating that the turn has changed.
     *
     * @param game game instance
     * @param currentTurnPlayer player who now has the turn
     * @param lastShotResult result of the last shot (useful for UI/UX feedback)
     * @return event DTO
     */
    public static GameEventDto turnChanged(Game game, Player currentTurnPlayer, ShotResult lastShotResult) {
        return new GameEventDto(
                GameEventType.TURN_CHANGED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "currentTurnPlayerName", currentTurnPlayer.getUsername(),
                        "lastShotResult", lastShotResult.name()
                )
        );
    }

    /**
     * Creates an event indicating that a shot has been fired.
     *
     * @param game game instance
     * @param attacker player who fired the shot
     * @param defender player whose board was targeted
     * @param x x-coordinate (0-based)
     * @param y y-coordinate (0-based)
     * @param result shot result
     * @return event DTO
     */
    public static GameEventDto shotFired(Game game, Player attacker, Player defender, int x, int y, ShotResult result) {
        return new GameEventDto(
                GameEventType.SHOT_FIRED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "shooterPlayerName", attacker.getUsername(),
                        "targetPlayerName", defender.getUsername(),
                        "x", x,
                        "y", y,
                        "result", result.name(),
                        "hit", (result == ShotResult.HIT || result == ShotResult.SUNK),
                        "shipSunk", (result == ShotResult.SUNK)
                )
        );
    }

    /**
     * Creates an event indicating that the game finished and who the winner is.
     *
     * @param game game instance
     * @param winner winning player
     * @return event DTO
     */
    public static GameEventDto gameFinished(Game game, Player winner) {
        return new GameEventDto(
                GameEventType.GAME_FINISHED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "winnerPlayerName", winner.getUsername()
                )
        );
    }

    /**
     * Creates an event indicating that the game was paused.
     *
     * @param game game instance
     * @param requestedBy player who requested the pause
     * @return event DTO
     */
    public static GameEventDto gamePaused(Game game, Player requestedBy) {
        return new GameEventDto(
                GameEventType.GAME_PAUSED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "requestedByPlayerName", requestedBy.getUsername()
                )
        );
    }

    /**
     * Creates an event indicating that a resume handshake is pending.
     *
     * <p>This project uses a two-player handshake for resume:
     * first player confirms -> WAITING, second player confirms -> RUNNING.
     *
     * @param game game instance
     * @param requestedBy player who initiated/confirmed resume
     * @return event DTO
     */
    public static GameEventDto gameResumePending(Game game, Player requestedBy) {
        return new GameEventDto(
                GameEventType.GAME_RESUME_PENDING,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "requestedByPlayerName", requestedBy.getUsername(),
                        "resumeReadyCount", game.getResumeReadyPlayerId() != null ? 1 : 0
                )
        );
    }

    /**
     * Creates an event indicating that the game was resumed.
     *
     * <p>Implementation detail:
     * {@link Map#of(Object, Object, Object, Object)} does not allow null values. Since the
     * current turn player name may be null (depending on resume state), a mutable map is used.
     *
     * @param game game instance
     * @param requestedBy player who completed the resume action
     * @param currentTurnPlayerName name of the current turn player (may be null)
     * @return event DTO
     */
    public static GameEventDto gameResumed(Game game, Player requestedBy, String currentTurnPlayerName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestedByPlayerName", requestedBy.getUsername());
        payload.put("currentTurnPlayerName", currentTurnPlayerName);

        return new GameEventDto(
                GameEventType.GAME_RESUMED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                payload
        );
    }

    /**
     * Creates an event indicating that a player forfeited the game and who the winner is.
     *
     * @param game game instance
     * @param forfeitingPlayer player who forfeited
     * @param winner winning player
     * @return event DTO
     */
    public static GameEventDto gameForfeited(Game game, Player forfeitingPlayer, Player winner) {
        return new GameEventDto(
                GameEventType.GAME_FORFEITED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "forfeitingPlayerName", forfeitingPlayer.getUsername(),
                        "winnerPlayerName", winner.getUsername()
                )
        );
    }

    /**
     * Creates an event indicating that a player has disconnected.
     *
     * <p>This event is sent when a WebSocket session is closed (browser closed,
     * network failure, etc.). The game will automatically be moved to WAITING status
     * and both players will need to use their resume tokens to rejoin.
     *
     * <p>Payload contains:
     * <ul>
     *   <li>{@code disconnectedPlayerName} - username of the disconnected player</li>
     *   <li>{@code message} - user-friendly message explaining what happened</li>
     * </ul>
     *
     * @param game game instance
     * @param disconnectedPlayer player who lost connection
     * @return event DTO
     */
    public static GameEventDto playerDisconnected(Game game, Player disconnectedPlayer) {
        return new GameEventDto(
                GameEventType.PLAYER_DISCONNECTED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "disconnectedPlayerName", disconnectedPlayer.getUsername(),
                        "message", "Spieler " + disconnectedPlayer.getUsername() +
                                " hat die Verbindung verloren. Das Spiel wurde pausiert."
                )
        );
    }

    /**
     * Creates an event indicating that a player has reconnected.
     *
     * <p>This event is sent when a previously disconnected player subscribes to
     * game events again. Note that this does NOT automatically resume the game -
     * both players still need to use their resume tokens to continue playing.
     *
     * <p>Payload contains:
     * <ul>
     *   <li>{@code reconnectedPlayerName} - username of the reconnected player</li>
     *   <li>{@code message} - user-friendly message</li>
     * </ul>
     *
     * @param game game instance
     * @param reconnectedPlayer player who reconnected
     * @return event DTO
     */
    public static GameEventDto playerReconnected(Game game, Player reconnectedPlayer) {
        return new GameEventDto(
                GameEventType.PLAYER_RECONNECTED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "reconnectedPlayerName", reconnectedPlayer.getUsername(),
                        "message", "Spieler " + reconnectedPlayer.getUsername() +
                                " ist wieder verbunden."
                )
        );
    }
}