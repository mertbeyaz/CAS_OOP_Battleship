package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;
import ch.battleship.battleshipbackend.domain.Game;
import ch.battleship.battleshipbackend.domain.Lobby;
import ch.battleship.battleshipbackend.domain.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record LobbyDto(
        String lobbyCode,
        String gameCode,
        String status,
        UUID myPlayerId,
        UUID myBoardId,
        List<PlayerInfoDto> players
) {

    public static LobbyDto fromEntity(Lobby lobby, Player myPlayer, Board myBoard) {
        Game game = lobby.getGame();

        // Map von PlayerId -> Board, damit wir nicht dauernd streamen müssen
        Map<UUID, UUID> boardIdsByPlayerId = game.getBoards().stream()
                .filter(b -> b.getOwner() != null)
                .collect(Collectors.toMap(
                        b -> b.getOwner().getId(),
                        Board::getId
                ));

        List<PlayerInfoDto> players = game.getPlayers().stream()
                .map(p -> new PlayerInfoDto(
                        p.getId(),
                        p.getUsername(),
                        boardIdsByPlayerId.get(p.getId()) // kann null sein, falls mal ein Player (noch) kein Board hat
                ))
                .toList();

        return new LobbyDto(
                lobby.getLobbyCode(),
                game.getGameCode(),
                lobby.getStatus().name(),
                myPlayer != null ? myPlayer.getId() : null,
                myBoard  != null ? myBoard.getId()  : null,
                players
        );
    }

    public record PlayerInfoDto(
            UUID id,
            String username,
            UUID boardId
    ) {}
}



