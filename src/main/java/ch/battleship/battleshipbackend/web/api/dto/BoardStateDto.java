package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;

import java.util.List;
import java.util.UUID;

public record BoardStateDto(
        UUID boardId,
        int width,
        int height,
        boolean locked,
        List<ShipPlacementDto> shipPlacements
) {
    public static BoardStateDto from(Board board) {
        return new BoardStateDto(
                board.getId(),
                board.getWidth(),
                board.getHeight(),
                board.isLocked(),
                board.getPlacements().stream()
                        .map(ShipPlacementDto::from)
                        .toList()
        );
    }
}
