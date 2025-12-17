package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;
import ch.battleship.battleshipbackend.domain.enums.ShotResult;

import java.time.Instant;
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
                        "playerId", player.getId().toString(),
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
                        "playerId", player.getId().toString(),
                        "playerName", player.getUsername()
                )
        );
    }

    public static GameEventDto gameStarted(Game game, Player firstTurnPlayer) {
        return new GameEventDto(
                GameEventType.GAME_STARTED,
                game.getGameCode(),
                game.getStatus(),
                Instant.now(),
                Map.of(
                        "currentTurnPlayerId", firstTurnPlayer.getId().toString(),
                        "currentTurnPlayerName", firstTurnPlayer.getUsername()
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
                        "currentTurnPlayerId", currentTurnPlayer.getId().toString(),
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
                        "attackerId", attacker.getId().toString(),
                        "attackerName", attacker.getUsername(),
                        "defenderId", defender.getId().toString(),
                        "defenderName", defender.getUsername(),
                        "x", x,
                        "y", y,
                        "result", result.name(),
                        "hit", (result == ShotResult.HIT || result == ShotResult.SUNK),
                        "shipSunk", (result == ShotResult.SUNK),
                        "currentTurnPlayerId", game.getCurrentTurnPlayerId() == null ? null : game.getCurrentTurnPlayerId().toString()
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
                        "winnerPlayerId", winner.getId().toString(),
                        "winnerPlayerName", winner.getUsername()
                )
        );
    }
}