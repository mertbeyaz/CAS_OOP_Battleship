package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Player;
import ch.battleship.battleshipbackend.domain.enums.GameEventType;
import ch.battleship.battleshipbackend.domain.enums.GameStatus;

import java.time.Instant;

public record GameEventDto(
        GameEventType type,
        String gameCode,
        String playerId,
        String playerName,
        GameStatus gameStatus,
        Instant timeStamp
) {
    public static GameEventDto of(GameEventType type, Game game, Player player) {
        return new GameEventDto(
                type,
                game.getGameCode(),
                player.getId().toString(),
                player.getUsername(),
                game.getStatus(),
                Instant.now()
        );
    }

    public static GameEventDto boardConfirmed(Game game, Player player) {
        return of(GameEventType.BOARD_CONFIRMED, game, player);
    }

    public static GameEventDto boardRerolled(Game game, Player player) {
        return of(GameEventType.BOARD_REROLLED, game, player);
    }
}