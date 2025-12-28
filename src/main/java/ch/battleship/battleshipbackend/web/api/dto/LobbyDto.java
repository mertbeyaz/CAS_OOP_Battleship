package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;

import java.util.UUID;

public record LobbyDto(
        String lobbyCode,
        String gameCode,
        String status,
        UUID myPlayerId,
        String myPlayerName,
        BoardStateDto myBoard
) {
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