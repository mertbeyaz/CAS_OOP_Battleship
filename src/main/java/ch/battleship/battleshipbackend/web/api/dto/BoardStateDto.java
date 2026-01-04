package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Board;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing the full state of a board including ship placements.
 *
 * <p>This DTO is typically used for:
 * <ul>
 *   <li>Returning the player's own board during setup (e.g. reroll/confirm)</li>
 *   <li>DEV/test endpoints for debugging</li>
 * </ul>
 *
 * <p>Security note:
 * This DTO includes ship placements and must therefore not be used to expose an opponent's board
 * in production gameplay, as that would allow cheating.
 *
 * @param boardId board identifier
 * @param width board width
 * @param height board height
 * @param locked whether the board is confirmed/locked by its owner
 * @param shipPlacements current ship placements on the board
 */
public record BoardStateDto(
        UUID boardId,
        int width,
        int height,
        boolean locked,
        List<ShipPlacementDto> shipPlacements
) {

    /**
     * Creates a {@code BoardStateDto} from the given domain {@link Board}.
     *
     * @param board domain board entity
     * @return mapped DTO including ship placements
     * @throws NullPointerException if {@code board} is null
     */
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
