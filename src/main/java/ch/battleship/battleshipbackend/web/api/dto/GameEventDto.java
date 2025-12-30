package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record GameEventDto(
        GameEventType type,
        String gameCode,
        GameStatus gameStatus,
        Instant timeStamp,
        Map<String, Object> payload
) {
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

    public static GameEventDto gameStarted(Game game,Player currentTurnPlayer) {
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
    //gameResumePending
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

    public static GameEventDto gameResumed(Game game, Player requestedBy, String currentTurnPlayerName) {
        // We should not use Map.of directly in GameEventDto, because of possible null reference
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

}