package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;

import java.util.UUID;

public record BoardDto(
        UUID id,
        int width,
        int height,
        UUID ownerId,
        String ownerUsername
) {
    public static BoardDto from(Board board) {
        return new BoardDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.getOwner().getId(),
                board.getOwner().getUsername()
        );
    }
}
