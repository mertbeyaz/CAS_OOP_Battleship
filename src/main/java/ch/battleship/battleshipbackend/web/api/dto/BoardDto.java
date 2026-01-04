package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;

import java.util.UUID;

/**
 * DTO representing basic, client-safe information about a board.
 *
 * <p>This DTO contains only metadata (size and ownership) and intentionally does not expose
 * ship placements or other hidden gameplay information.
 *
 * @param id board identifier
 * @param width board width
 * @param height board height
 * @param ownerId id of the owning player
 * @param ownerUsername username of the owning player
 */
public record BoardDto(
        UUID id,
        int width,
        int height,
        UUID ownerId,
        String ownerUsername
) {

    /**
     * Creates a {@code BoardDto} from the given domain {@link Board}.
     *
     * @param board domain board entity
     * @return mapped DTO
     * @throws NullPointerException if {@code board} or {@code board.getOwner()} is null
     */
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
