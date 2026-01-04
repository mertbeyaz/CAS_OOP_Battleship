package ch.battleship.battleshipbackend.web.api.dto;

import ch.battleship.battleshipbackend.domain.Player;

import java.util.UUID;

/**
 * DTO representing a player in a client-safe form.
 *
 * <p>This DTO exposes only the player's identifier and username and intentionally
 * omits any game-internal or sensitive information.
 *
 * @param id unique identifier of the player
 * @param username display name of the player
 */
public record PlayerDto(
        UUID id,
        String username
) {

    /**
     * Creates a {@code PlayerDto} from the given domain {@link Player}.
     *
     * @param player domain player entity
     * @return mapped DTO
     * @throws NullPointerException if {@code player} is null
     */
    public static PlayerDto from(Player player) {
        return new PlayerDto(
                player.getId(),
                player.getUsername()
        );
    }
}
